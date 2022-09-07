package com.OGApps.futprices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import kotlin.system.exitProcess

class ConfirmDialog : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_dialog)
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)

        btnConfirm.setOnClickListener {
            exitProcess(0)
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }


    override fun onStart() {
        super.onStart()
        FloatingPriceService.collapsedView?.visibility = View.GONE
        FloatingPriceService.overlayActive = true
    }

    override fun onStop() {
        FloatingPriceService.collapsedView?.visibility = View.VISIBLE
        FloatingPriceService.overlayActive = false
        super.onStop()
    }

}