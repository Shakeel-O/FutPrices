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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.json.JSONObject




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
//                    Toast.makeText(this, "here is where i will screenshot", Toast.LENGTH_SHORT)
//                        .show()

                    Log.i(
                        TAG,
                        "heres the screenshot image: $latestImage"
                    )
                    bitmap = convertToCroppedBitmap(latestImage)
//                    bitmap = convertToBitmap(latestImage!!)

                    scanImage(bitmap)
                    { resultText ->
                        if (resultText != "") {
                            Log.i(TAG, "resultText: $resultText")
                            val stats = PlayerSearch.getPlayerStats(resultText)
                            if (stats == "") {
                                scanFailed(bitmap)
                                bitmap!!.recycle()
                                Log.i(TAG, "wrong screen: $resultText")
                                return@scanImage
                            }
                            else{
                                getFilteredPlayers(stats)
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
                    Toast.makeText(this, "no image found, try again in a few seconds", Toast.LENGTH_SHORT).show()
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

    fun getFilteredPlayers(urlParams: String) {
// Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://www.futbin.org/futbin/api/getFilteredPlayers?${urlParams}"

// Request a string response from the provided URL.

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                // Display the first 500 characters of the response string.
                Log.i(TAG, "response here: $response")
                val help = Response(response)
                Log.i(TAG, "response help: $help")
                Toast.makeText(this, "Player: ${help.data?.get(0)?.get("playername")} Price: ${help.data?.get(0)?.get("ps_LCPrice")}", Toast.LENGTH_SHORT).show()

            },
            {response ->
                Log.i(TAG, "response error: ${response.message}")
            })
        Log.i(TAG, "final url: ${url}")

// Add the request to the RequestQueue.
        queue.add(stringRequest)

//        val client = HttpClient.newBuilder().build();
//        val request = HttpRequest.newBuilder()
//            .uri(URI.create("https://www.futbin.org/futbin/api/getFilteredPlayers?${urlParams}"))
//            .build();
//
//        val response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        println(response.body())
    }

    class Response(json: String) : JSONObject(json) {
        val type: String? = this.optString("type")
        val data = this.optJSONArray("data")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
            ?.map { Foo(it.toString()) } // transforms each JSONObject of the array into Foo
    }

    class Foo(json: String) : JSONObject(json) {
        val id = this.optInt("id")
        val title: String? = this.optString("title")
    }

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