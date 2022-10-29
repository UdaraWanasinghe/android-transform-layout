package com.aureusapps.android.zoomlayout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val gesturedDetectorListeners = mutableSetOf<ZoomGestureDetectorListener>()
    private val gestureDetectorListener = object : ZoomGestureDetectorListener {
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
    private val zoomGestureDetector = ZoomGestureDetector(context, gestureDetectorListener)

    var isZoomEnabled
        get() = zoomGestureDetector.isZoomEnabled
        set(value) {
            zoomGestureDetector.isZoomEnabled = value
        }

    var isScaleEnabled
        get() = zoomGestureDetector.isScaleEnabled
        set(value) {
            zoomGestureDetector.isScaleEnabled = value
        }

    var isRotationEnabled
        get() = zoomGestureDetector.isRotationEnabled
        set(value) {
            zoomGestureDetector.isRotationEnabled = value
        }

    var isTranslationEnabled
        get() = zoomGestureDetector.isTranslationEnabled
        set(value) {
            zoomGestureDetector.isTranslationEnabled = value
        }

    var isFlingEnabled
        get() = zoomGestureDetector.isFlingEnabled
        set(value) {
            zoomGestureDetector.isFlingEnabled = value
        }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isZoomEnabled) return true
        zoomGestureDetector.onTouchEvent(event)
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return zoomGestureDetector.onTouchEvent(event)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        canvas.setMatrix(zoomGestureDetector.drawMatrix)
        return super.drawChild(canvas, child, drawingTime)
    }

    fun scaleUp(stepSize: Float = 0.2f) {
        zoomGestureDetector.scaleUp(stepSize)
    }

    fun scaleDown(stepSize: Float = 0.2f) {
        zoomGestureDetector.scaleDown(stepSize)
    }

}