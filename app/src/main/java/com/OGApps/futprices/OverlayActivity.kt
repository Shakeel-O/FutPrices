package com.OGApps.futprices

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OverlayActivity : AppCompatActivity() {
    lateinit var bmp: Bitmap
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay)

        val byteArray = intent.getByteArrayExtra("image")
        bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)

        val imageView = findViewById<ImageView>(R.id.currentScreenshot)
        imageView.setImageBitmap(bmp)

    }

    override fun onDestroy() {
        super.onDestroy()
        if (bmp != null) {
            bmp.recycle()
        }
    }
}