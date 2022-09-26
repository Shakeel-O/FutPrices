package com.OGApps.futprices

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.*
import android.content.res.Resources
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream

class FloatingPriceService : Service(), View.OnClickListener  {
    companion object {

        fun scanImage(bitmap: Bitmap, callback: (Text) -> Unit) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
//                    Log.i(TAG, "processed image: $inputImage \b visionText: ${result.text}")
                    callback(result)

                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "processing image error: $e")
                }
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


        const val TAG = "FloatingPriceService"
        const val RESULT_CODE = "RESULT_CODE"
        const val DATA = "DATA"
        const val ACTION = "ACTION"
        const val START = "START"
        const val STOP = "STOP"
        const val SCREENCAP_NAME = "screencap"
        var overlayActive = false
        internal var imageReader: ImageReader? = null
        private var mediaProjection: MediaProjection? = null
        private var mHandler: Handler? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var IMAGES_PRODUCED = 0
        var mWidth = 0
        var mHeight = 0
        private var initialised = false

        @SuppressLint("StaticFieldLeak")
        lateinit var results: ImageView
        lateinit var image: Image

        private var mFloatingWidget: View? = null
        val expandedView: View?
            get() {
                return mFloatingWidget?.findViewById(R.id.expanded_container)
            }
        val collapsedView: View?
            get() {
                return mFloatingWidget?.findViewById(R.id.collapse_view)
            }
    }


    private lateinit var playerListView: ListView

    private lateinit var windowManager: WindowManager
    private lateinit var gIntent: Intent

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

    private fun scanFailed(bitmap: Bitmap) {
        //Convert to byte array
        val stream = ByteArrayOutputStream()
        val overlayIntent = Intent(this, OverlayActivity::class.java)
        overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var currSize: Int
        var currQuality = 100
        val maxSizeBytes = 1000000


        do {
            bitmap.compress(Bitmap.CompressFormat.JPEG, currQuality, stream)
            currSize = stream.toByteArray().size
            // limit quality by 5 percent every time
            currQuality -= 5
        } while (currSize >= maxSizeBytes && currQuality  >50)
//        val byteArray: ByteArray = stream.toByteArray()
        startActivity(overlayIntent)
    }

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
        val height: Int = Resources.getSystem().displayMetrics.heightPixels

        params.x = 0
        params.y = height/2

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(mFloatingWidget, params)
        PlayerSearch.appContext = applicationContext
        playerListView = mFloatingWidget!!.findViewById(R.id.player_list_view)
        playerListView.setOnItemClickListener { parent, _: View, position, _ ->
            val player = parent.getItemAtPosition(position) as Player
            val textToCopy = player.price
            Toast.makeText(applicationContext, "Copied:\n${player.price}", Toast.LENGTH_SHORT)
                .show()
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("text", textToCopy)
            clipboardManager.setPrimaryClip(clipData)
        }
        playerListView.isLongClickable = true
        playerListView.setOnItemLongClickListener { parent, _, position, _ ->
            val player = parent.getItemAtPosition(position) as Player
            val playerId = player.id
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.futbin.com/23/player/$playerId")
            )
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(browserIntent)
            return@setOnItemLongClickListener true
        }
        val closeButtonCollapsed = mFloatingWidget?.findViewById<View>(R.id.close_btn) as ImageView
        closeButtonCollapsed.setOnClickListener {
            val confirmIntent = Intent(this, ConfirmDialog::class.java)
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(confirmIntent)
        }

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
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!moving && !longClick) {
                                v.performClick()
                            }
                            moving = false
                            longClick = false

                            handler?.removeCallbacks(mLongPressed)
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val xDifference = (params.x - (initialX - (event.rawX.minus(initialTouchX))))
                            val yDifference = (params.y - (initialY - (event.rawY.minus(initialTouchY))))
                            if (xDifference < 50 && xDifference > -50 && yDifference < 50 && yDifference > -50)
                            {
                                moving = false
                            }else{
                                handler?.removeCallbacks(mLongPressed)
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
    }


    override fun onClick(p0: View?) {
        if (!moving && !overlayActive) {
            image = imageReader?.acquireNextImage()!!
            Log.i(TAG, "onClick: $image")
            var bitmap: Bitmap?
            try {
                bitmap = convertToCroppedBitmap(image)
//                    bitmap = convertToBitmap(image)

                scanImage(bitmap)
                { result ->
                    if (!PlayerSearch.searchPlayer(result, image, playerListView))
                    {
                        bitmap = convertToBitmap(image)
                        if (!overlayActive)
                        {
                            scanFailed(bitmap!!)
                        }
                        bitmap!!.recycle()
                        collapsedView?.visibility = View.VISIBLE
                        expandedView?.visibility = View.GONE
                    }
                    else {
                        expandedView?.visibility = View.VISIBLE
                    }

                }
                IMAGES_PRODUCED++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun longClick() {
        longClick =true
        MainActivity.openWebApp(false)
//        startService(Intent(baseContext, FloatingPriceService::class.java))

//        Toast.makeText(this,"long clicked", Toast.LENGTH_SHORT).show()
    }


    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mediaProjection == null) {

            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().displayMetrics.densityDpi
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
        get() = mFloatingWidget == null || mFloatingWidget!!.findViewById<View>(R.id.collapse_view).visibility == View.VISIBLE

    override fun onDestroy() {
        if (mFloatingWidget != null) windowManager.removeView(mFloatingWidget)
        super.onDestroy()
        Toast.makeText(this, "service stopped", Toast.LENGTH_SHORT).show()

    }
}

