package com.OGApps.futprices

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Menu
import android.view.View
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.OGApps.futprices.databinding.ActivityMainBinding


class MainActivity : ComponentActivity() {


    companion object {
        private const val REQUEST_CAPTURE = 1
        var mDisplay: Display? = null

        var projection: MediaProjection? = null
    }

    private lateinit var mainActivity: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    @RequiresApi(Build.VERSION_CODES.O)
    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "onCreate: media projection result: ${result.data?.action} & ${result.data}")
            val service = OverlayService.getStartIntent(this, result.resultCode, result.data)
            startForegroundService(service)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainActivity.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mDisplay = display
        } else {
            @Suppress("DEPRECATION")
            mDisplay = windowManager.defaultDisplay
        }

        var startButton = findViewById<Button>(R.id.start_service)

        startButton.setOnClickListener(View.OnClickListener {
            getPermissions()
            startProjection()
        })
        var stopButton = findViewById<Button>(R.id.stop_service)
        stopButton.setOnClickListener(View.OnClickListener {
            stopProjection()
        })
    }

//    private fun registerForActivityResult(startActivityForResult: Any, any: Any): Any {
//
//    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // check if permission is already allowed
            var canDraw: Boolean = true
            var intent: Intent? = null
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            canDraw = Settings.canDrawOverlays(this)
            if (!canDraw && intent != null) {
                startActivity(intent)
            }


        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startProjection() {
        mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopProjection() {
        startMediaProjection.unregister()
        stopService(OverlayService.getStopIntent(this))
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CAPTURE) {
//            if (resultCode == RESULT_OK) {
//                val intent = Intent(this, OverlayService::class.java)
//                    .setAction(OverlayService.ACTION_ENABLE_CAPTURE)
//                startService(intent)
//                projection = data?.let { mediaProjectionManager.getMediaProjection(resultCode, it) }
//                Toast.makeText(this,"something is startinh", Toast.LENGTH_SHORT).show()
//
//            } else {
//                projection = null
//                Toast.makeText(this,"error occured", Toast.LENGTH_SHORT).show()
//            }
//        }
//        finish()
//    }
}

