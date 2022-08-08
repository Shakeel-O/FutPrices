package com.OGApps.futprices

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer.RGBA_8888
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class OverlayService : Service(), View.OnTouchListener, View.OnClickListener {


    companion object {
        internal fun getStopIntent(context: Context?): Intent? {
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent? {
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            return intent
        }

        val TAG = "ScreenCaptureService"
        val RESULT_CODE = "RESULT_CODE"
        val DATA = "DATA"
        val ACTION = "ACTION"
        val START = "START"
        val STOP = "STOP"
        val SCREENCAP_NAME = "screencap"
        internal var imageReader: ImageReader? = null
        private var mediaProjection: MediaProjection? = null
        private var mHandler: Handler? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var IMAGES_PRODUCED = 0
        private var mWidth = 0
        private var mHeight = 0
        private var initialised = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var gIntent: Intent

    private lateinit var overlayButton: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private var moving = false
//    private val capture = Capture(this)

    private val mStoreDir: String? = null
    private var mDensity = 0
    private val mRotation = 0
    private var mDisplay: Display? = null


    private class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler?.post(Runnable {
                virtualDisplay?.release()
                imageReader?.setOnImageAvailableListener(null, null)
                mediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            })
        }
    }

    private fun isStartCommand(intent: Intent): Boolean {
        return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA) && intent.hasExtra(ACTION) &&
                intent.getStringExtra(ACTION) == START)
    }


    private fun isStopCommand(intent: Intent): Boolean {
        return intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP
    }

    private class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            var fos: FileOutputStream? = null
            var bitmap: Bitmap? = null
            val image = reader.acquireLatestImage()
            try {
                if (image != null) {
                    Log.i(
                        TAG,
                        "heres the image: $image"
                    )
                    val planes: Array<Image.Plane> = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding: Int = rowStride - pixelStride * mWidth

                    // create bitmap
                    bitmap = Bitmap.createBitmap(
                        mWidth + rowPadding / pixelStride,
                        mHeight,
                        Bitmap.Config.ARGB_8888
                    );
                    bitmap.copyPixelsFromBuffer(buffer);

                    val recognizer =
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val inputImage = InputImage.fromBitmap(bitmap, 0)

                    val result = recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            Log.i(TAG, "processed image: $inputImage \b visionText: $result")
                            val resultText = result.text
                            if (resultText != "") {
                                Log.i("results", "resultText: $resultText")
                                if (resultText.contains("player details", ignoreCase = true) || resultText.contains("item details", ignoreCase = true)
                                ) {
                                    getPlayerDetails(result)
                                }
                            }

                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "processing image failed: $IMAGES_PRODUCED$inputImage")
                            Log.e(TAG, "processing image error: $e")
                        }

                    image.close()
                    IMAGES_PRODUCED++
//                        Log.i(
//                            TAG,
//                            "captured image: $IMAGES_PRODUCED$bitmap"
//                        )
                }
//                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (fos != null) {
                    try {
                        fos!!.close()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                }
                if (bitmap != null) {
                    bitmap!!.recycle()
                }
            }
        }

        private fun getPlayerDetails(result: Text) {
            for (block in result.textBlocks) {
                val blockText = block.text
                val blockCornerPoints = block.cornerPoints
                val blockFrame = block.boundingBox
                //TODO: find index of block containing 'pac' 'sho' 'pas' etc. split them up. get index before for name, index after for position
                Log.i("results", "blockText: $blockText")
                for (line in block.lines) {
                    val lineText = line.text
                    val lineCornerPoints = line.cornerPoints
                    val lineFrame = line.boundingBox
                    Log.i("results", "lineText: $lineText")

                    for (element in line.elements) {
                        val elementText = element.text
                        val elementCornerPoints = element.cornerPoints
                        val elementFrame = element.boundingBox
                        Log.i("results", "elementText: $elementText")

                    }
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        Toast.makeText(this, "service started", Toast.LENGTH_SHORT).show()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayButton = ImageView(this)
        overlayButton.setImageResource(R.mipmap.fut_prices_logo)
        overlayButton.setOnClickListener(this)
        overlayButton.setOnTouchListener(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        params.x = 0
        params.y = 100

        windowManager.addView(overlayButton, params)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        gIntent = intent
        Log.i(ContentValues.TAG, "onStartCommand: $intent ")
        Log.i(ContentValues.TAG, "onStartCommand: ${isStartCommand(intent)}  ")
        if (isStartCommand(intent)) {
            if (!initialised) {
                initialised = true
                // create notification
                val notification = NotificationUtils.getNotification(this)
                startForeground(notification.first, notification.second)
                // start projection
                val resultCode = intent.getIntExtra(
                    RESULT_CODE,
                    Activity.RESULT_CANCELED
                )
                val data =
                    intent.getParcelableExtra<Intent>(DATA)
                startProjection(resultCode, data!!)
            }
            // start capture handling thread
            object : Thread() {
                override fun run() {
                    Looper.prepare()
                    mHandler = Handler(Looper.getMainLooper())
                    Toast.makeText(this@OverlayService, "LOOPER", Toast.LENGTH_SHORT).show()

                    Looper.loop()
                }
            }.start()

        } else if (isStopCommand(intent)) {
            Log.i(TAG, "onStartCommand: stop command called")
            stopProjection()
            stopSelf()
            initialised = false
        } else {
//            stopSelf()
            initialised = false
        }
        return super.onStartCommand(intent, flags, startId)

    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0.0f
    private var initialTouchY = 0.0f

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {


        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {

                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                Log.i(TAG, "onTouch, moving $moving")
                if (!moving) {
                    view!!.performClick()
                }
                moving = false
            }
            MotionEvent.ACTION_MOVE -> {
                moving = true
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(overlayButton, params)
            }
        }
        return true
    }

    override fun onClick(p0: View?) {
        if (!moving) {
            Toast.makeText(this, "here is where i will screenshot", Toast.LENGTH_SHORT).show()
//            MainActivity.projection?.run {
//                capture.run(this) {
//                    capture.stop()
//                    // save bitmap
//                }
//            }
//            val content = window.decorView
//            val w = content.width
//            val h = content.height
//            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//            content.draw(Canvas(bitmap))
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        Log.i(ContentValues.TAG, "starting projection")

        if (mediaProjection == null) {

            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            Log.i(ContentValues.TAG, "startProjection: $mediaProjection ")
            if (mediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().displayMetrics.densityDpi
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = MainActivity.mDisplay

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register media projection stop callback
                mediaProjection!!.registerCallback(
                    MediaProjectionStopCallback(),
                    mHandler
                )
            }
        }
    }

    private fun stopProjection() {
        mHandler!!.post {
            mediaProjection?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(overlayButton)
        Toast.makeText(this, "service stopped", Toast.LENGTH_SHORT).show()

    }

    private fun getVirtualDisplayFlags(): Int {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }

    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().displayMetrics.widthPixels
        mHeight = Resources.getSystem().displayMetrics.heightPixels

        // start capture reader
        imageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            SCREENCAP_NAME,
            mWidth,
            mHeight,
            mDensity,
            getVirtualDisplayFlags(),
            imageReader!!.surface,
            null,
            mHandler
        )
        imageReader!!.setOnImageAvailableListener(
            ImageAvailableListener(),
            mHandler
        )
    }
}