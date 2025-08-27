package com.rrgpt

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class FullScreenImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root container
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        // PhotoView for zooming
        val photoView = PhotoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
            isClickable = true // important for touch handling
        }

        // Back button
        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x55000000) // semi-transparent
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.START or Gravity.TOP
                marginStart = (16 * resources.displayMetrics.density).toInt()
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            setOnClickListener { finish() }
        }



        // Add views
        container.addView(photoView)
        container.addView(backBtn)
        backBtn.bringToFront() // ensures button is always on top

        // Make container focusable to let button receive touches
        container.isFocusableInTouchMode = true
        container.requestFocus()

        setContentView(container)

        // Load image
        intent.getStringExtra("image_uri")?.let { uriStr ->
            val uri = Uri.parse(uriStr)
            Glide.with(this)
                .load(uri)
                .into(photoView)
        }
    }

    // Optional: back press also closes
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
