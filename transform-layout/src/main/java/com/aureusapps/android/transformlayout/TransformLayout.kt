package com.aureusapps.android.transformlayout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class TransformLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    var isScaleEnabled
        get() = transformGestureDetector.isScaleEnabled
        set(value) {
            transformGestureDetector.isScaleEnabled = value
        }

    var isRotationEnabled
        get() = transformGestureDetector.isRotateEnabled
        set(value) {
            transformGestureDetector.isRotateEnabled = value
        }

    var isTranslationEnabled
        get() = transformGestureDetector.isTranslateEnabled
        set(value) {
            transformGestureDetector.isTranslateEnabled = value
        }

    var isFlingEnabled
        get() = transformGestureDetector.isFlingEnabled
        set(value) {
            transformGestureDetector.isFlingEnabled = value
        }

    var isTransformEnabled: Boolean = true

    private val gesturedDetectorListeners = mutableSetOf<TransformGestureDetectorListener>()
    private val gestureDetectorListener = object : TransformGestureDetectorListener {
        override fun onTransformStart(px: Float, py: Float, matrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onTransformStart(px, py, matrix) }
        }

        override fun onTransformUpdate(px: Float, py: Float, oldMatrix: Matrix, newMatrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onTransformUpdate(px, py, oldMatrix, newMatrix) }
        }

        override fun onTransformComplete(px: Float, py: Float, matrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onTransformComplete(px, py, matrix) }
        }

        override fun onSingleTap(px: Float, py: Float) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onSingleTap(px, py) }
        }
    }
    private val transformGestureDetector = TransformGestureDetector(context, gestureDetectorListener)


    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isTransformEnabled) return true
        transformGestureDetector.onTouchEvent(event)
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return transformGestureDetector.onTouchEvent(event)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        canvas.setMatrix(transformGestureDetector.drawMatrix)
        return super.drawChild(canvas, child, drawingTime)
    }


}