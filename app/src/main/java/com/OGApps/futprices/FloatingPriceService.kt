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
import android.icu.text.NumberFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class FloatingPriceService : Service(),View.OnTouchListener, View.OnClickListener  {
    companion object {

        fun scanImage(bitmap: Bitmap, callback: (Text) -> Unit) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            var failed = false
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val result = recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    for (block in result.textBlocks) {
                        val blockText = block.text
                        val blockCornerPoints = block.cornerPoints
                        val blockFrame = block.boundingBox
                        Log.i(TAG, "processed image: $inputImage \b blocktext: ${blockText}")

                        for (line in block.lines) {
                            val lineText = line.text
                            val lineCornerPoints = line.cornerPoints
                            val lineFrame = line.boundingBox
                            Log.i(TAG, "processed image: $inputImage \b lineText: ${lineText}")
                            for (element in line.elements) {
                                val elementText = element.text
                                val elementCornerPoints = element.cornerPoints
                                val elementFrame = element.boundingBox
                            }
                        }
                    }
                    Log.i(TAG, "processed image: $inputImage \b visionText: ${result.text}")
                    callback(result)

                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "processing image failed: $IMAGES_PRODUCED$inputImage")
                    Log.e(TAG, "processing image error: $e")
                }
//            Log.i(TAG, "other method: ${result.result}")
        }

        internal fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, FloatingPriceService::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
            val intent = Intent(context, FloatingPriceService::class.java)
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


        val TAG = "FloatingPriceService"
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
    val expandedView: View?
        get() {
            return mFloatingWidget?.findViewById<View>(R.id.expanded_container)
        }
    private lateinit var playerListView: ListView

    var layoutFlag: Int? = null
    private lateinit var windowManager: WindowManager
    private lateinit var gIntent: Intent

    private lateinit var params: WindowManager.LayoutParams
    private var moving = false
    private var longClick = false

    private var mDensity = 0
    private var mDisplay: Display? = null

    var handler: Handler? = null
    var mLongPressed = Runnable { Log.i("", "Long press!")
        longClick()
    }
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

    private fun scanFailed(bitmap: Bitmap) {
        //Convert to byte array
        Toast.makeText(this, "scan failed", Toast.LENGTH_SHORT).show()
        val stream = ByteArrayOutputStream()
        val overlayIntent: Intent = Intent(this, OverlayActivity::class.java)
        overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var currSize: Int
        var currQuality = 100
        val maxSizeBytes = 1400000


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
    private var mFloatingWidget: View? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.layout_floating_price, null)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        val height: Int = Resources.getSystem().getDisplayMetrics().heightPixels;

        params.x = 0
        params.y = height/2

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager!!.addView(mFloatingWidget, params)
        val collapsedView = mFloatingWidget?.findViewById<View>(R.id.collapse_view)
        val closeButtonCollapsed = mFloatingWidget?.findViewById<View>(R.id.close_btn) as ImageView
        playerListView = mFloatingWidget!!.findViewById<ListView>(R.id.player_list_view)
        closeButtonCollapsed.setOnClickListener { stopSelf() }
        val closeButton = mFloatingWidget?.findViewById<View>(R.id.close_button) as ImageView
        closeButton.setOnClickListener {
            collapsedView?.visibility = View.VISIBLE
            expandedView?.visibility = View.GONE
        }
        val rootContainer = mFloatingWidget?.findViewById<View>(R.id.root_container)
            rootContainer?.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            handler?.postDelayed(mLongPressed, ViewConfiguration.getLongPressTimeout().toLong())
                            Log.i(TAG, "onTouch, action down moving $moving")

                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            Log.i(TAG, "onTouch, action up moving $moving")
                            if (!moving && !longClick) {
                                if (isViewCollapsed) {
                                    collapsedView?.visibility = View.GONE
                                    expandedView?.visibility = View.VISIBLE
                                }
                                v!!.performClick()
                            }
                            moving = false
                            longClick = false

                            handler?.removeCallbacks(mLongPressed);
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val xDifference = (params.x - (initialX - (event.rawX.minus(initialTouchX))))
                            val yDifference = (params.y - (initialY - (event.rawY.minus(initialTouchY))))
                            if (xDifference < 100 && xDifference > -100 && yDifference < 100 && yDifference > -100)
                            {
                                moving = false
                                Log.i(TAG, "onTouch,action move accidental")
                            }else{
                                Log.i(TAG, "onTouch,action move moving $moving")
                                Log.i(
                                    TAG,
                                    "onTouch,action move x ${params.x} > ${initialX - (event.rawX - initialTouchX)}"
                                )
                                Log.i(
                                    TAG,
                                    "onTouch,action move y ${params.y} > ${initialY - (event.rawY - initialTouchY)}"
                                )
//                Log.i(TAG, "onTouch,action move y $moving")

                                handler?.removeCallbacks(mLongPressed);
                                moving = true
                                params.x = initialX - (event.rawX - initialTouchX).toInt()
                                params.y = initialY + (event.rawY - initialTouchY).toInt()
                                windowManager.updateViewLayout(mFloatingWidget, params)
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        rootContainer?.setOnClickListener(this)
        //start fifa companion app
        val launchIntent = packageManager.getLaunchIntentForPackage("com.ea.gp.fifaultimate")
        Log.i(ContentValues.TAG, "fifa package: $launchIntent ")

        launchIntent?.let { startActivity(it) }
    }

    override fun onClick(p0: View?) {
        if (!moving) {
            Log.i(TAG, "onClick: $image")
            val latestImage: Image? = imageReader?.acquireNextImage()
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
                    { result ->
                        val resultText = result.text
                        if (resultText != "") {
                            Log.i(TAG, "resultText: $resultText")
                            val multiScan = PlayerSearch.multiplePlayers(resultText)
                            if (multiScan != null) {
                                if (!PlayerSearch.multiplePlayers(resultText)!!) {
                                    val stats = PlayerSearch.getPlayerStats(resultText)
                                    Log.i(FloatingPriceService.TAG, "parsing stats: $stats")

                                    if (stats.isEmpty()) {
                                        scanFailed(bitmap)
                                        bitmap.recycle()
                                        Log.i(TAG, "wrong screen: $resultText")
                                        return@scanImage
                                    } else {
                                        getFilteredPlayers( ParseURL.urlEncodeUTF8(stats)){help :JSONObject ->
                                            val playerList = arrayOfNulls<Player>(1)
                                            val player = Player.getPlayer(help)
                                            playerList[0] = player
                                            val adapter = PlayerAdapter(this, playerList)
                                            playerListView.adapter = adapter

                                            Toast.makeText(
                                                this,
                                                "Player: ${player.name} \nPrice: ${player.price}",Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else {
                                    scanImage(convertToBitmap(latestImage))
                                    {resultTextFull ->

//                                        val stats = PlayerSearch.getMultiplePlayerStats(resultTextFull)
                                        var uncheckedMap = PlayerSearch.getMultiplePlayerStats(resultTextFull)
                                        var maps = uncheckedMap.filter { map -> map != null }.toTypedArray()
                                        val playerList = arrayOfNulls<Player>(maps.size)
                                        maps.forEachIndexed {i,map ->
                                            Log.i(FloatingPriceService.TAG, "parsing map: $map & ${map != null} & index: $i & size: ${maps.size}")

                                            if (map != null) {
                                                getFilteredPlayers(ParseURL.urlEncodeUTF8(map)) { help: JSONObject ->
                                                    Log.i(TAG, "getting player with : $help")

                                                    val player = Player.getPlayer(help)
                                                    Log.i(TAG, "player acquired : $player")
                                                    Log.i(TAG, "adding to playerList: $player")

                                                    playerList[i] = player

                                                    for (map2 in playerList) {
                                                        Log.i(FloatingPriceService.TAG, "checking playerList: $map2")
                                                    }
                                                    Toast.makeText(
                                                        this,
                                                        "Player: ${player.name} \nPrice: ${player.price}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    if (playerList.filterNotNull().size == maps.size) {
                                                        val adapter =
                                                            PlayerAdapter(this, playerList)
                                                        playerListView.adapter = adapter
                                                    }

                                                }

                                                for (map in playerList) {
                                                    Log.i(FloatingPriceService.TAG, "checking playerList: $map")
                                                }
                                                Log.i(
                                                    FloatingPriceService.TAG,
                                                    "adding adapter with: $playerList and ${playerList.size}"
                                                )

                                            }
                                        }
                                    }
//                                    val stats: Array<HashMap<String, String>> = stats = PlayerSearch.getPlayersStats(resultText)
//                                    for (stat in stats)
//                                    {
//                                        getFilteredPlayers(ParseURL.urlEncodeUTF8(stats))
//                                    }
                                }
                            }
                            else
                            {
                                scanFailed(bitmap)
                                bitmap.recycle()
                                Log.i(TAG, "wrong screen: $resultText")
                                return@scanImage
                            }
                        } else {
                            Log.i(TAG, "no text found: $resultText")
                            scanFailed(bitmap)
                            bitmap.recycle()
                        }
                        Log.i(TAG, "isViewCollapsed: $isViewCollapsed")

                            expandedView?.visibility = View.VISIBLE
                        latestImage.close()
                    }
                    IMAGES_PRODUCED++
                } else {
                    Toast.makeText(this, "no image found, try again in a few seconds", Toast.LENGTH_SHORT).show()
                }
                Log.i(TAG, "isViewCollapsed: $isViewCollapsed")


            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                Log.i(TAG, "isViewCollapsed: $isViewCollapsed")

                if (isViewCollapsed) {
                    expandedView?.visibility = View.VISIBLE
                }
                if (bitmap != null) {
//                    bitmap!!.recycle()
                }
            }
        }
    }

    private fun longClick() {
        longClick =true
        startService(Intent(baseContext, FloatingPriceService::class.java))

        Toast.makeText(this,"long clicked", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getFilteredPlayers(urlParams: String, callback: (JSONObject) -> Unit) {

// Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://www.futbin.org/futbin/api/getFilteredPlayers?${urlParams}"

// Request a string response from the provided URL.

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.i(TAG, "response here: $response")
                val help = FloatingPriceService.Response(response)
                Log.i(TAG, "response help: $help")
                callback(help)

            },
            { response ->
                Log.i(TAG, "response error: ${response.message}")
            })
        Log.i(TAG, "final url: ${url}")

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }

    private fun displayResults(response: String?) {

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
            val handlerThread = HandlerThread("HandlerThread")
            handlerThread.start()
            handler =Handler(handlerThread.looper)



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



    private val isViewCollapsed: Boolean
        private get() = mFloatingWidget == null || mFloatingWidget!!.findViewById<View>(R.id.collapse_view).visibility == View.VISIBLE

    override fun onDestroy() {
        if (mFloatingWidget != null) windowManager!!.removeView(mFloatingWidget)
        super.onDestroy()
        Toast.makeText(this, "service stopped", Toast.LENGTH_SHORT).show()

    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }
}