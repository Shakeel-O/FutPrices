package com.OGApps.futprices

import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.OGApps.futprices.FloatingPriceService.Companion.image

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
//        imageView.setimage

    }


    override fun onStart() {
        super.onStart()
        FloatingPriceService.overlayActive = true
    }

    override fun onStop() {
        super.onStop()
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