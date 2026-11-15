package com.accel.loader

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class AboutDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.about_back).setOnClickListener { dismiss() }
        bindDetails()
    }

    override fun onStart() {
        super.onStart()
        window?.let { dialogWindow ->
            dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            dialogWindow.statusBarColor = ContextCompat.getColor(context, R.color.loader_background)
            dialogWindow.navigationBarColor = ContextCompat.getColor(context, R.color.loader_background)
            dialogWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun bindDetails() {
        val details = listOf(
            Detail(R.id.detail_developer, R.drawable.ic_user, "Developer", "Accel"),
            Detail(R.id.detail_version, R.drawable.ic_package, "App Version", "1.6.59")
        )

        details.forEach { detail ->
            val row = findViewById<View>(detail.id)
            row.findViewById<ImageView>(R.id.about_detail_icon).setImageResource(detail.icon)
            row.findViewById<TextView>(R.id.about_detail_label).text = detail.label
            row.findViewById<TextView>(R.id.about_detail_value).text = detail.value
        }
    }

    private data class Detail(
        val id: Int,
        val icon: Int,
        val label: String,
        val value: String
    )
}
