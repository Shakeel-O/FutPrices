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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Menu
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.OGApps.futprices.databinding.ActivityMainBinding
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {


    companion object {
        var mDisplay: Display? = null
    }

    private lateinit var mainActivity: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var resultCode by Delegates.notNull<Int>()
    private lateinit var resultData: Intent
    private lateinit var drawOverlayButton: ToggleButton
    private lateinit var screenCapButton: ToggleButton
    private lateinit var startButton: Button
    private lateinit var howToButton: TextView
    private lateinit var webAppCBox: CheckBox
    private lateinit var companionCBox: CheckBox


    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "onCreate: media projection result: ${result.data?.action} & ${result.data}")
resultCode = result.resultCode
            resultData = result.data!!
            screenCapButton.isChecked = true
        }
        else
        {
//            stopProjection()
            screenCapButton.isChecked = false
            startButton.isEnabled = false
        }

        Toast.makeText(this, " start media... am i checked?: ${drawOverlayButton.isChecked && screenCapButton.isChecked}", Toast.LENGTH_SHORT).show()
        startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

1        // hide overlay from screen shots/records
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        mainActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainActivity.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mDisplay = display
        } else {
            @Suppress("DEPRECATION")
            mDisplay = windowManager.defaultDisplay
        }
//        setContext(this)
        companionCBox = findViewById(R.id.cBoxComp)
        companionCBox.setOnCheckedChangeListener { buttonView, isChecked -> if (isChecked) webAppCBox.isChecked = false}
        webAppCBox = findViewById(R.id.cBoxWeb)
        webAppCBox.setOnCheckedChangeListener { buttonView, isChecked -> if (isChecked) companionCBox.isChecked = false}
        startButton = findViewById<Button>(R.id.start_service)
        startButton.setOnClickListener {

            Toast.makeText(this, "am i active?: ${startButton.isEnabled}", Toast.LENGTH_SHORT).show()
           val service = FloatingPriceService.getStartIntent(this, resultCode, resultData)
            startForegroundService(service)
            if (companionCBox.isChecked)
            {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.ea.gp.fifaultimate")
            launchIntent?.let { startActivity(it) }
            }
            if (webAppCBox.isChecked) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://www.ea.com/fifa/ultimate-team/web-app")
                )
                startActivity(browserIntent)
            }
//            val service = FloatingPriceService.getStartIntent(this)
//            startForegroundService(service)
        }
        drawOverlayButton = findViewById<ToggleButton>(R.id.btnDrawOverlay)
        drawOverlayButton.isChecked = Settings.canDrawOverlays(this)

        drawOverlayButton.setOnClickListener {
            val intent: Intent?
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            val canDraw: Boolean = Settings.canDrawOverlays(this) && drawOverlayButton.isChecked
            if (!canDraw) {
                startActivity(intent)
            }
            drawOverlayButton.isChecked = Settings.canDrawOverlays(this)
//            startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked
        }
        screenCapButton = findViewById(R.id.btnScreenCap)
//        startProjection()

        screenCapButton.isChecked = false
        screenCapButton.setOnCheckedChangeListener { buttonView, isChecked ->

//            getPermissions()
            if (isChecked)
            {
                startProjection()
            }
            else
            {
                stopProjection()
            }
            Toast.makeText(this, "am i checked?: ${isChecked}", Toast.LENGTH_SHORT).show()
            startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked;


        }

        howToButton = findViewById(R.id.btnHowTo)
        howToButton.setOnClickListener {

                val helpIntent = Intent(
                    baseContext,HelpActivity::class.java
                )
                startActivity(helpIntent)
        }

        startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked;


//        val stopButton = findViewById<Button>(R.id.stop_service)
//        stopButton.setOnClickListener {
//            stopProjection()
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        drawOverlayButton.isChecked = Settings.canDrawOverlays(this)
        startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked
    }

    private fun getPermissions() {
        // check if permission is already allowed
        val intent: Intent?
        intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        val canDraw: Boolean = Settings.canDrawOverlays(this)
        if (!canDraw) {
            startActivity(intent)
        }

    }

    private fun startProjection() {
        mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startMediaProjection.launch(intent)
//        screenCapButton.isChecked = mediaProjectionManager.getMediaProjection(resultCode,resultData) != null
    }

    private fun stopProjection() {
        stopService(FloatingPriceService.getStopIntent(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        startMediaProjection.unregister()
    }
}

