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

    private val gesturedDetectorListeners = mutableSetOf<TransformGestureDetectorListener>()
    private val gestureDetectorListener = object : TransformGestureDetectorListener {
        override fun onZoomStart(downPoint: Position, drawMatrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onZoomStart(downPoint, drawMatrix) }
        }

        override fun onZoomUpdate(oldDrawMatrix: Matrix, newDrawMatrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onZoomUpdate(oldDrawMatrix, newDrawMatrix) }
        }

        override fun onZoomComplete(upPoint: Position, drawMatrix: Matrix) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onZoomComplete(upPoint, drawMatrix) }
        }

        override fun onSingleTap(tapPoint: Position) {
            invalidate()
            gesturedDetectorListeners.forEach { it.onSingleTap(tapPoint) }
        }
    }
    private val transformGestureDetector = TransformGestureDetector(context, gestureDetectorListener)

    var isZoomEnabled
        get() = transformGestureDetector.isZoomEnabled
        set(value) {
            transformGestureDetector.isZoomEnabled = value
        }

    var isScaleEnabled
        get() = transformGestureDetector.isScaleEnabled
        set(value) {
            transformGestureDetector.isScaleEnabled = value
        }

    var isRotationEnabled
        get() = transformGestureDetector.isRotationEnabled
        set(value) {
            transformGestureDetector.isRotationEnabled = value
        }

    var isTranslationEnabled
        get() = transformGestureDetector.isTranslationEnabled
        set(value) {
            transformGestureDetector.isTranslationEnabled = value
        }

    var isFlingEnabled
        get() = transformGestureDetector.isFlingEnabled
        set(value) {
            transformGestureDetector.isFlingEnabled = value
        }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isZoomEnabled) return true
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

    fun scale(stepSize: Float = 0.2f) {
        transformGestureDetector.setScaling(stepSize)
    }

}