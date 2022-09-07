package com.OGApps.futprices

import android.graphics.Bitmap
import android.media.Image
import android.opengl.Visibility
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.OGApps.futprices.FloatingPriceService.Companion.collapsedView
import com.OGApps.futprices.FloatingPriceService.Companion.image
import kotlin.system.exitProcess

class OverlayActivity : AppCompatActivity() {
    lateinit var bmp: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay)
        FloatingPriceService.overlayActive = true

//        val byteArray = intent.getByteArrayExtra("image")
//        bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
        val planes: Array<Image.Plane> = image.planes as Array<Image.Plane>
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * FloatingPriceService.mWidth

        // create bitmap
        bmp = Bitmap.createBitmap(
            FloatingPriceService.mWidth + rowPadding / pixelStride,
            FloatingPriceService.mHeight,
            Bitmap.Config.ARGB_8888
        )
        buffer?.rewind()
        bmp.copyPixelsFromBuffer(buffer)
        val imageView = findViewById<ImageView>(R.id.currentScreenshot)

        imageView.setImageBitmap(bmp)
        val btnClose = findViewById<Button>(R.id.btnClose)
        btnClose.setOnClickListener{ v ->
            Log.i("overlay fail screen", "onCreate: finishing ")
            finish()
        }
    }


    override fun onStart() {
        super.onStart()
        collapsedView?.visibility = View.GONE

        FloatingPriceService.overlayActive = true
    }

    override fun onStop() {
        super.onStop()
        collapsedView?.visibility = View.VISIBLE
        FloatingPriceService.overlayActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingPriceService.overlayActive = false
        if (bmp != null) {
            image.close()
            bmp.recycle()
        }
    }
}