package com.OGApps.futprices

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import com.OGApps.futprices.databinding.ActivityMainBinding
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {


    companion object {
        var mDisplay: Display? = null
        fun openWebApp(web: Boolean) {
            val context = PlayerSearch.appContext
            if (!web) {
                val launchIntent =
                    context.packageManager.getLaunchIntentForPackage("com.ea.gp.fifaultimate")
                context.startActivity(launchIntent)
            } else {
                val launchIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://www.ea.com/fifa/ultimate-team/web-app")
                )
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
            }
        }
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


    private val startMediaProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                Log.i(
                    TAG,
                    "onCreate: media projection result: ${result.data?.action} & ${result.data}"
                )
                resultCode = result.resultCode
                resultData = result.data!!
                screenCapButton.isChecked = true
            } else {
//            stopProjection()
                screenCapButton.isChecked = false
                startButton.isEnabled = false
            }

            Toast.makeText(
                this,
                " start media... am i checked?: ${drawOverlayButton.isChecked && screenCapButton.isChecked}",
                Toast.LENGTH_SHORT
            ).show()
            startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // hide overlay from screen shots/records
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
        PlayerSearch.appContext = applicationContext

        companionCBox = findViewById(R.id.cBoxComp)
        companionCBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) webAppCBox.isChecked = false
        }
        webAppCBox = findViewById(R.id.cBoxWeb)
        webAppCBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) companionCBox.isChecked = false
        }
        startButton = findViewById(R.id.start_service)
        startButton.setOnClickListener {

            val service = FloatingPriceService.getStartIntent(this, resultCode, resultData)
            startForegroundService(service)
            if (companionCBox.isChecked) {
                openWebApp(false)
            }
            if (webAppCBox.isChecked) {
                openWebApp(true)
            }
        }
        drawOverlayButton = findViewById(R.id.btnDrawOverlay)
        drawOverlayButton.isChecked = Settings.canDrawOverlays(this)

        drawOverlayButton.setOnClickListener {
            val intent: Intent?
            intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            val canDraw: Boolean = Settings.canDrawOverlays(this) && drawOverlayButton.isChecked
            if (!canDraw) {
                startActivity(intent)
            }
            drawOverlayButton.isChecked = Settings.canDrawOverlays(this)
        }
        screenCapButton = findViewById(R.id.btnScreenCap)

        screenCapButton.isChecked = false
        screenCapButton.setOnCheckedChangeListener { _, isChecked ->

            if (isChecked) {
                startProjection()
            } else {
                stopProjection()
            }
            startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked


        }

        howToButton = findViewById(R.id.btnHowTo)
        howToButton.setOnClickListener {

            val helpIntent = Intent(
                baseContext, HelpActivity::class.java
            )
            startActivity(helpIntent)
        }

        startButton.isEnabled = drawOverlayButton.isChecked && screenCapButton.isChecked
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

    private fun startProjection() {
        mediaProjectionManager =
            getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startMediaProjection.launch(intent)
    }

    private fun stopProjection() {
        stopService(FloatingPriceService.getStopIntent(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        startMediaProjection.unregister()
    }
}

