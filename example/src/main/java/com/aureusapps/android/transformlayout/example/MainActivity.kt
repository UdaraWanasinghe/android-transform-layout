package com.aureusapps.android.transformlayout.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aureusapps.android.transformlayout.TransformLayout
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var transformLayout: TransformLayout
    private lateinit var zoomInButton: MaterialButton
    private lateinit var zoomOutButton: MaterialButton
    private lateinit var drawButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transformLayout = findViewById(R.id.zoom_layout)
        zoomInButton = findViewById(R.id.zoom_in_button)
        zoomOutButton = findViewById(R.id.zoom_out_button)
        drawButton = findViewById(R.id.draw_button)

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