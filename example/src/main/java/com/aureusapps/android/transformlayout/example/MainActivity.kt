package com.aureusapps.android.transformlayout.example

import android.graphics.Matrix
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aureusapps.android.extensions.getRotation
import com.aureusapps.android.extensions.getScaling
import com.aureusapps.android.extensions.translation
import com.aureusapps.android.transformlayout.TransformGestureDetectorListener
import com.aureusapps.android.transformlayout.TransformLayout
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var transformLayout: TransformLayout
    private lateinit var scaleUpButton: MaterialButton
    private lateinit var scaleDownButton: MaterialButton
    private lateinit var resetTransformButton: MaterialButton
    private lateinit var drawButton: MaterialButton
    private lateinit var rotateLeftButton: MaterialButton
    private lateinit var rotateRightButton: MaterialButton
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transformLayout = findViewById(R.id.transform_layout)
        scaleUpButton = findViewById(R.id.scale_up_button)
        scaleDownButton = findViewById(R.id.scale_down_button)
        resetTransformButton = findViewById(R.id.reset_transform_button)
        rotateLeftButton = findViewById(R.id.rotate_left_button)
        rotateRightButton = findViewById(R.id.rotate_right_button)
        drawButton = findViewById(R.id.draw_button)
        logTextView = findViewById(R.id.log_text_view)

        transformLayout.addTransformGestureDetectorListener(object :
            TransformGestureDetectorListener {
            private val f = "%.2f"
            private val b = StringBuilder()
            private val m = Matrix()
            private var t = System.currentTimeMillis()

            override fun onTransformStart(px: Float, py: Float, matrix: Matrix) {
                appendToLogText("Transform started", matrix, px, py)
            }

            override fun onTransformUpdate(
                px: Float,
                py: Float,
                oldMatrix: Matrix,
                newMatrix: Matrix
            ) {
                if (System.currentTimeMillis() - t > 200) {
                    t = System.currentTimeMillis()
                    appendToLogText("Transform updated", newMatrix, px, py)
                }
            }

            override fun onTransformComplete(px: Float, py: Float, matrix: Matrix) {
                appendToLogText("Transform completed", matrix, px, py)
            }

            override fun onSingleTap(px: Float, py: Float): Boolean {
                b.insert(0, "Single tap detected[px:$px, py:$py]\n")
                logTextView.text = b.toString()
                return true
            }

            override fun onLongPress(px: Float, py: Float): Boolean {
                b.insert(0, "Long press detected[px:$px, py:$py]")
                logTextView.text = b.toString()
                return true
            }

            private fun appendToLogText(text: String, matrix: Matrix, px: Float, py: Float) {
                m.set(matrix)
                val (sx, sy) = m.getScaling(px, py)
                val r = m.getRotation(px, py)
                val (tx, ty) = m.translation
                val t = "$text[" +
                        "tx:${f.format(tx)}, " +
                        "ty:${f.format(ty)}, " +
                        "sx:${f.format(sx)}, " +
                        "sy:${f.format(sy)}, " +
                        "r:$${f.format(r)}]\n"
                b.insert(0, t)
                logTextView.text = b.toString()
            }

        })
        scaleUpButton.setOnClickListener {
            transformLayout.concatTransform(scaling = 1.2f)
        }
        scaleDownButton.setOnClickListener {
            transformLayout.concatTransform(scaling = 0.8f)
        }
        resetTransformButton.setOnClickListener {
            transformLayout.resetTransform()
        }
        rotateLeftButton.setOnClickListener {
            transformLayout.concatTransform(rotation = -15f)
        }
        rotateRightButton.setOnClickListener {
            transformLayout.concatTransform(rotation = 15f)
        }
        drawButton.isChecked = !transformLayout.isTransformEnabled
        drawButton.addOnCheckedChangeListener { _, isChecked ->
            transformLayout.isTransformEnabled = !isChecked
        }
    }

}