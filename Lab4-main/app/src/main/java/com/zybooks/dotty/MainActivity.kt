package com.zybooks.dotty
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zybooks.dotty.DotsView.DotsGridListener
import java.util.Locale
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : AppCompatActivity() {
    private val dotsGame = DotsGame.getInstance()
    private lateinit var dotsView: DotsView
    private lateinit var movesRemainingTextView: TextView
    private lateinit var scoreTextView: TextView

    private lateinit var soundEffects: SoundEffects


    private var photoFile: File? = null
    private lateinit var photoImageView: ImageView
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var saveButton: Button

    // For adding brightness
    private var multColor = -0x1
    private var addColor = 0


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        movesRemainingTextView = findViewById(R.id.moves_remaining_text_view)
        scoreTextView = findViewById(R.id.score_text_view)
        dotsView = findViewById(R.id.dots_view)

        findViewById<Button>(R.id.new_game_button).setOnClickListener { newGameClick() }

        dotsView.setGridListener(gridListener)

        soundEffects = SoundEffects.getInstance(applicationContext)

        startNewGame()


        photoImageView = findViewById(R.id.photo)

        saveButton = findViewById(R.id.save_button)
        saveButton.setOnClickListener { savePhotoClick() }
        saveButton.isEnabled = false

        findViewById<Button>(R.id.take_photo_button).setOnClickListener { takePhotoClick() }

        brightnessSeekBar = findViewById(R.id.brightness_seek_bar)
        brightnessSeekBar.visibility = View.INVISIBLE

        brightnessSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                changeBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
    override fun onDestroy() {
        super.onDestroy()
        soundEffects.release()
    }

    private val gridListener = object : DotsGridListener {
        override fun onDotSelected(dot: Dot, status: DotSelectionStatus) {
            // Ignore selections when game is over
            if (dotsGame.isGameOver) return

            // Play first tone when first dot is selected
            if (status == DotSelectionStatus.First) {
                soundEffects.resetTones()
            }
            // Add/remove dot to/from selected dots
            val addStatus = dotsGame.processDot(dot)

            if (addStatus == DotStatus.Added) {
                soundEffects.playTone(true)
            } else if (addStatus == DotStatus.Removed) {
                soundEffects.playTone(false)
            }
            // If done selecting dots then replace selected dots and display new moves and score
            if (status === DotSelectionStatus.Last) {
                if (dotsGame.selectedDots.size > 1) {
                    dotsView.animateDots()
                    dotsGame.finishMove()
                    updateMovesAndScore()
                } else {
                    dotsGame.clearSelectedDots()
                }
            }

            // Display changes to the game
            dotsView.invalidate()
        }

        override fun onAnimationFinished() {
            dotsGame.finishMove()
            dotsView.invalidate()
            updateMovesAndScore()

            if (dotsGame.isGameOver) {
                soundEffects.playGameOver()
            }
        }
    }



    private fun newGameClick() {
        val screenHeight = this.window.decorView.height.toFloat()
        val moveBoardOff = ObjectAnimator.ofFloat(
            dotsView, "translationY", screenHeight)
        moveBoardOff.duration = 700
        moveBoardOff.start()

        moveBoardOff.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                startNewGame()

                // Animate from above the screen down to default location
                val moveBoardOn = ObjectAnimator.ofFloat(
                    dotsView, "translationY", -screenHeight, 0f)
                moveBoardOn.duration = 700
                moveBoardOn.start()
            }
        })
    }


    private fun startNewGame() {
        dotsGame.newGame()
        dotsView.invalidate()
        updateMovesAndScore()
    }

    private fun updateMovesAndScore() {
        movesRemainingTextView.text = String.format(Locale.getDefault(), "%d", dotsGame.movesLeft)
        scoreTextView.text = String.format(Locale.getDefault(), "%d", dotsGame.score)
    }


    private fun takePhotoClick() {

        // Create the File for saving the photo
        photoFile = createImageFile()

        // Create a content URI to grant camera app write permission to mPhotoFile
        val photoUri = FileProvider.getUriForFile(
            this,
            "com.zybooks.dotty.file-provider", photoFile!!
        )

        // Start camera app
        takePicture.launch(photoUri)
    }

    private val takePicture = registerForActivityResult(
        TakePicture()
    ) { success ->
        if (success) {
            displayPhoto()
            brightnessSeekBar.progress = 100
            brightnessSeekBar.visibility = View.VISIBLE
            changeBrightness(brightnessSeekBar.progress)
            saveButton.isEnabled = true
        }
    }

    private fun createImageFile(): File {

        // Create a unique image filename
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFilename = "photo_$timeStamp.jpg"

        // Get file path where the app can save a private image
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, imageFilename)
    }

    private fun displayPhoto() {
        // Get ImageView dimensions
        val targetWidth = photoImageView.width
        val targetHeight = photoImageView.height

        // Get bitmap dimensions
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(photoFile!!.absolutePath, bmOptions)
        val photoWidth = bmOptions.outWidth
        val photoHeight = bmOptions.outHeight

        // Determine how much to scale down the image
        val scaleFactor = Math.min(photoWidth / targetWidth, photoHeight / targetHeight)

        // Decode the image file into a smaller bitmap that fills the ImageView
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor
        val bitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath, bmOptions)

        // Display smaller bitmap
        photoImageView.setImageBitmap(bitmap)
    }

    private fun changeBrightness(brightness: Int) {
        // TODO: Change brightness
    }

    private fun savePhotoClick() {
        // TODO: Save the altered photo
    }
}