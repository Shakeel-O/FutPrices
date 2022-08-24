package com.OGApps.futprices

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
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
import java.io.ByteArrayOutputStream


class OverlayService() : Service(), View.OnTouchListener, View.OnClickListener {


    companion object {

        fun scanImage(bitmap: Bitmap, callback: (String) -> Unit) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            var failed = false
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val result = recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    Log.i(TAG, "processed image: $inputImage \b visionText: ${result.text}")
callback(result.text)

                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "processing image failed: $IMAGES_PRODUCED$inputImage")
                    Log.e(TAG, "processing image error: $e")
                }
//            Log.i(TAG, "other method: ${result.result}")
        }

//        private fun scanFailed(bitmap: Bitmap, overlayService: OverlayService?) =
//            overlayService?.scanFailed(
//                bitmap
//            )

        internal fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
            val intent = Intent(context, OverlayService::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            return intent
        }


        fun convertToBitmap(image: Image): Bitmap {
            val bitmap: Bitmap?
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
            )
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        fun convertToCroppedBitmap(image: Image): Bitmap {
//            var bitmap: Bitmap? = null
            val planes: Array<Image.Plane> = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding: Int = rowStride - pixelStride * mWidth

            // create bitmap
            val origBitmap = Bitmap.createBitmap(
                mWidth + rowPadding / pixelStride,
                mHeight,
                Bitmap.Config.ARGB_8888
            )
            val bitmap = Bitmap.createScaledBitmap(origBitmap,
                origBitmap.width,
                origBitmap.height /(5/2),false
            )
//            bitmap= Bitmap.createBitmap(origBitmap,10,10,origBitmap.width-11,
//                origBitmap.height /(5/2))
            val c = Canvas(bitmap)
            val paint = Paint()
            val cm = ColorMatrix()
            cm.setSaturation(0F)
            val f = ColorMatrixColorFilter(cm)
            val left = 0f
            paint.colorFilter = f
            c.drawBitmap(bitmap, left, left, paint)
            buffer.rewind()
            bitmap?.copyPixelsFromBuffer(buffer)
            return bitmap
        }


        val TAG = "OverlayService"
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

        @SuppressLint("StaticFieldLeak")
        lateinit var results: ImageView
        var image: Image? = null
    }

    var layoutFlag: Int? = null
    private lateinit var windowManager: WindowManager
    private lateinit var gIntent: Intent

    private lateinit var overlayButton: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private var moving = false

    private var mDensity = 0
    private var mDisplay: Display? = null


    private class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler?.post {
                virtualDisplay?.release()
                imageReader?.setOnImageAvailableListener(null, null)
                mediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            }
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
            var bitmap: Bitmap? = null
            image = reader.acquireLatestImage()
            try {

                val image1 = image
                if (image1 != null) {
                    Log.i(
                        TAG,
                        "heres the image: $image1"
                    )
                    bitmap = convertToCroppedBitmap(image1)

                    scanImage(bitmap){}




                    IMAGES_PRODUCED++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image?.close()
                if (bitmap != null) {
                    bitmap.recycle()
                }
            }
        }

    }

    fun scanFailed(bitmap: Bitmap) {
        //Convert to byte array
        Toast.makeText(this, "scan failed", Toast.LENGTH_SHORT).show()
        val stream = ByteArrayOutputStream()
        val overlayIntent: Intent = Intent(this, OverlayActivity::class.java)
//                var overlayIntent = Intent(MainActivity.getContext(), OverlayActivity::class.java)
        overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        var currSize: Int
        var currQuality = 100
        var maxSizeBytes = 1400000


        do {
            bitmap.compress(Bitmap.CompressFormat.JPEG, currQuality, stream)
            currSize = stream.toByteArray().size
            // limit quality by 5 percent every time
            currQuality -= 5
        } while (currSize >= maxSizeBytes)
        val byteArray: ByteArray = stream.toByteArray()
        overlayIntent.putExtra("image", byteArray)
        startActivity(overlayIntent)
    }

    fun getPlayerDetails(result: Text) {
        for (block in result.textBlocks) {
            val blockText = block.text
            val blockCornerPoints = block.cornerPoints
            val blockFrame = block.boundingBox
            //TODO: find index of block containing 'pac' 'sho' 'pas' etc. split them up. get index before for name, index after for position
            Log.i(TAG+"results", "blockText: $blockText")
            val array = arrayOf(blockText)
//                if ()
//                {
//                    return
//                }
            for (line in block.lines) {
                val lineText = line.text
                val lineCornerPoints = line.cornerPoints
                val lineFrame = line.boundingBox
                Log.i(TAG+"results", "lineText: $lineText")

                for (element in line.elements) {
                    val elementText = element.text
                    val elementCornerPoints = element.cornerPoints
                    val elementFrame = element.boundingBox
                    Log.i(TAG+"results", "elementText: $elementText")

                }
            }
        }
    }

    fun getPlayerDetailsList(result: Text) {
        for (block in result.textBlocks) {
            val blockText = block.text
            val blockCornerPoints = block.cornerPoints
            val blockFrame = block.boundingBox
            //TODO: find index of block containing 'pac' 'sho' 'pas' etc. split them up. get index before for name, index after for position
            Log.i("results", "blockText: $blockText")
            val array = arrayOf(blockText)
//                if ()
//                {
//                    return
//                }
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

    override fun onCreate() {
        super.onCreate()

        Toast.makeText(this, "service started", Toast.LENGTH_SHORT).show()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        results = ImageView(this)
        overlayButton = ImageView(this)
        overlayButton.setImageResource(R.mipmap.fut_prices_logo)
        overlayButton.setOnClickListener(this)
        overlayButton.setOnTouchListener(this)

        layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END

        params.x = 0
        params.y = 400

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
//                    Toast.makeText(this@OverlayService, "LOOPER", Toast.LENGTH_SHORT).show()

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

//    override fun onHandleIntent(intent: Intent?) {
//        TODO("Not yet implemented")
//    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0.0f
    private var initialTouchY = 0.0f
//    private lateinit var mainActivity: OverlayBinding

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
                params.x = initialX - (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(overlayButton, params)
            }
        }
        return true
    }

    override fun onClick(p0: View?) {
        if (!moving) {
            Log.i(TAG, "onClick: $image")
            val latestImage: Image? = imageReader?.acquireLatestImage()
            var bitmap: Bitmap? = null
            try {
                if (latestImage != null) {
                    Toast.makeText(this, "here is where i will screenshot", Toast.LENGTH_SHORT)
                        .show()

                    Log.i(
                        TAG,
                        "heres the screenshot image: $latestImage"
                    )
                    bitmap = convertToCroppedBitmap(latestImage)
//                    bitmap = convertToBitmap(latestImage!!)

//                    results.setImageBitmap(bitmap)
//                    params = WindowManager.LayoutParams(
//                        WindowManager.LayoutParams.WRAP_CONTENT / 2,
//                        WindowManager.LayoutParams.WRAP_CONTENT / 2,
//                        layoutFlag!!,
//                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                        PixelFormat.TRANSLUCENT
//                    )

                    scanImage(bitmap)
                    {resultText ->
                        if (resultText != "") {
                            Log.i(TAG, "resultText: $resultText")
                            if (resultText.contains("player details", ignoreCase = true) ||
                                resultText.contains("item details", ignoreCase = true)
                            ) {
                                /* example of successful screenshot
                                *     O 30 A4 14%
    PLAYER DETAILS
    472,505 0
    95
    ST
    Arsenal
    p
    LACAZETTE
    94 PAC
    96 SHO
    88 PAS
    95 DRI
    57 DEF
    93 PHY
    POS: ST*/


                            } else if (resultText.contains("unassigned", ignoreCase = true) ||
                                resultText.contains("my club players", ignoreCase = true)
                            ) {
                            } else {
                                scanFailed(bitmap)
                                bitmap!!.recycle()
                                Log.i(TAG, "wrong screen: $resultText")
                            }
                        } else {
                            Log.i(TAG, "no text found: $resultText")
                            scanFailed(bitmap)
                            bitmap!!.recycle()
                        }
                    }
                    latestImage.close()
                    IMAGES_PRODUCED++
                } else {
                    Toast.makeText(this, "no image found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (bitmap != null) {
//                    bitmap!!.recycle()
                }
            }
        }
    }

//    private fun detectText() {
//        val keypoint = MatOfKeyPoint()
//        val listpoint: List<KeyPoint>
//        var kpoint: KeyPoint
//        val mask: Mat = Mat.zeros(mGrey.size(), CvType.CV_8UC1)
//        var rectanx1: Int
//        var rectany1: Int
//        var rectanx2: Int
//        var rectany2: Int
//        val imgsize: Int = mGrey.height() * mGrey.width()
//        val zeos = Scalar(0, 0, 0)
//        val contour2: List<MatOfPoint> = ArrayList()
//        val kernel = Mat(1, 50, CvType.CV_8UC1, Scalar.all(255.0))
//        val morbyte = Mat()
//        val hierarchy = Mat()
//        var rectan3: Rect
//        //
//        val detector: FeatureDetector = FeatureDetector
//            .create(FeatureDetector.MSER)
//        detector.detect(mGrey, keypoint)
//        listpoint = keypoint.toList()
//        //
//        for (ind in listpoint.indices) {
//            kpoint = listpoint[ind]
//            rectanx1 = (kpoint.pt.x - 0.5 * kpoint.size).toInt()
//            rectany1 = (kpoint.pt.y - 0.5 * kpoint.size).toInt()
//            rectanx2 = kpoint.size.toInt()
//            rectany2 = kpoint.size.toInt()
//            if (rectanx1 <= 0) rectanx1 = 1
//            if (rectany1 <= 0) rectany1 = 1
//            if (rectanx1 + rectanx2 > mGrey.width()) rectanx2 = mGrey.width() - rectanx1
//            if (rectany1 + rectany2 > mGrey.height()) rectany2 = mGrey.height() - rectany1
//            val rectant = Rect(rectanx1, rectany1, rectanx2, rectany2)
//            try {
//                val roi = Mat(mask, rectant)
//                roi.setTo(CONTOUR_COLOR)
//            } catch (ex: java.lang.Exception) {
//                Log.d("mylog", "mat roi error " + ex.message)
//            }
//        }
//        Imgproc.morphologyEx(mask, morbyte, Imgproc.MORPH_DILATE, kernel)
//        Imgproc.findContours(
//            morbyte, contour2, hierarchy,
//            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE
//        )
//        for (ind in contour2.indices) {
//            rectan3 = Imgproc.boundingRect(contour2[ind])
//            rectan3 = Imgproc.boundingRect(contour2[ind])
//            if (rectan3.area() > 0.5 * imgsize || rectan3.area() < 100 || rectan3.width / rectan3.height < 2) {
//                val roi = Mat(morbyte, rectan3)
//                roi.setTo(zeos)
//            } else Imgproc.rectangle(
//                mRgba, rectan3.br(), rectan3.tl(),
//                CONTOUR_COLOR
//            )
//        }
//    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mediaProjection == null) {

            mediaProjection = mpManager.getMediaProjection(resultCode, data)
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
//        imageReader!!.setOnImageAvailableListener(
//            ImageAvailableListener(),
//            mHandler
//        )
    }
}