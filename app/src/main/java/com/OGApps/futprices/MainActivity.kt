package com.OGApps.futprices

import android.app.Service
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Menu
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.OGApps.futprices.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.util.*


class MainActivity : ComponentActivity() {


    companion object {
        var mDisplay: Display? = null
//        private lateinit var context: Context
//
//        fun setContext(con: Context) {
//            context=con
//        }
//        fun getContext(): Context {
//            return context
//        }
    }

    private lateinit var mainActivity: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    @RequiresApi(Build.VERSION_CODES.O)
    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "onCreate: media projection result: ${result.data?.action} & ${result.data}")
            val service = FloatingPriceService.getStartIntent(this, result.resultCode, result.data)
            startForegroundService(service)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OpenCVLoader.initDebug()
        mainActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainActivity.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mDisplay = display
        } else {
            @Suppress("DEPRECATION")
            mDisplay = windowManager.defaultDisplay
        }
//        setContext(this)

        val startButton = findViewById<Button>(R.id.start_service)

        startButton.setOnClickListener {
            getPermissions()
            startProjection()

//            val intent = Intent()
//            intent.setPackage("com.ea.gp.fifaultimate")
//
//            val pm: PackageManager = packageManager
//            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
//            Collections.sort(resolveInfos, ResolveInfo.DisplayNameComparator(pm))
//            Log.i(TAG, "fifa package resolve: $resolveInfos ")
//
//            if (resolveInfos.isNotEmpty()) {
//                val launchable: ResolveInfo = resolveInfos[0]
//                val activity: ActivityInfo = launchable.activityInfo
//                val name = ComponentName(
//                    activity.applicationInfo.packageName,
//                    activity.name
//                )
//                val i = Intent(Intent.ACTION_MAIN)
//                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
//                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
//                i.component = name
//                Log.i(TAG, "fifa package: $i ")
//                startActivity(i)
//            }
        }
        val stopButton = findViewById<Button>(R.id.stop_service)
        stopButton.setOnClickListener {
            stopProjection()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // check if permission is already allowed
            val intent: Intent?
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            val canDraw: Boolean = Settings.canDrawOverlays(this)
            if (!canDraw) {
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
//        startMediaProjection.unregister()
        stopService(FloatingPriceService.getStopIntent(this))
    }
}

