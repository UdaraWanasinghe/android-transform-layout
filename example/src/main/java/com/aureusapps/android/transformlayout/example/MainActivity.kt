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
    private lateinit var scaleUpButton: MaterialButton
    private lateinit var scaleDownButton: MaterialButton
    private lateinit var resetTransformButton: MaterialButton
    private lateinit var drawButton: MaterialButton
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transformLayout = findViewById(R.id.transform_layout)
        scaleUpButton = findViewById(R.id.scale_up_button)
        scaleDownButton = findViewById(R.id.scale_down_button)
        resetTransformButton = findViewById(R.id.reset_transform_button)
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
        scaleUpButton.setOnClickListener {
            transformLayout.gestureDetector.concatTransform(scaling = 1.2f)
        }
        scaleDownButton.setOnClickListener {
            transformLayout.gestureDetector.concatTransform(scaling = 0.8f)
        }
        resetTransformButton.setOnClickListener {
            transformLayout.gestureDetector.resetTransform()
        }
        drawButton.isChecked = !transformLayout.isTransformEnabled
        drawButton.addOnCheckedChangeListener { _, isChecked ->
            transformLayout.isTransformEnabled = !isChecked
        }
    }

}