package com.aureusapps.android.transformlayout.example

import android.graphics.Matrix
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aureusapps.android.transformlayout.TransformGestureDetectorListener
import com.aureusapps.android.transformlayout.TransformLayout
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var transformLayout: TransformLayout
    private lateinit var zoomInButton: MaterialButton
    private lateinit var zoomOutButton: MaterialButton
    private lateinit var drawButton: MaterialButton
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transformLayout = findViewById(R.id.zoom_layout)
        zoomInButton = findViewById(R.id.zoom_in_button)
        zoomOutButton = findViewById(R.id.zoom_out_button)
        drawButton = findViewById(R.id.draw_button)
        logTextView = findViewById(R.id.log_text_view)

        val logTextBuilder = StringBuilder()
        transformLayout.addTransformGestureDetectorListener(object : TransformGestureDetectorListener {
            override fun onTransformStart(px: Float, py: Float, matrix: Matrix) {
                logTextBuilder.insert(0, "Transform started: $px, $py\n")
                logTextView.text = logTextBuilder.toString()
            }

            override fun onTransformUpdate(px: Float, py: Float, oldMatrix: Matrix, newMatrix: Matrix) {

            }

            override fun onTransformComplete(px: Float, py: Float, matrix: Matrix) {
                logTextBuilder.insert(0, "Transform completed: $px, $py\n")
                logTextView.text = logTextBuilder.toString()
            }

            override fun onSingleTap(px: Float, py: Float) {
                logTextBuilder.insert(0, "Single tap detected: $px, $py\n")
                logTextView.text = logTextBuilder.toString()
            }
        })
        zoomInButton.setOnClickListener {
        }
        zoomOutButton.setOnClickListener {
        }
        drawButton.isChecked = !transformLayout.isTransformEnabled
        drawButton.addOnCheckedChangeListener { _, isChecked ->
            transformLayout.isTransformEnabled = !isChecked
        }
    }

}