package com.aureusapps.android.transformlayout

import android.annotation.SuppressLint

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withMatrix

@SuppressLint("ClickableViewAccessibility")

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

    var isTransformEnabled = false

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
        // This will always receive ACTION_DOWN event.
        // If we return true, all the following touch events will be
        // dispatched to this view and onInterceptTouchEvent() won't be called again.
        // As long as we return false, we will receive following events here.
        return isTransformEnabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Touch events will be dispatched here only if they were intercepted or
        // children didn't consume them.
        if (isTransformEnabled) {
            // Gesture detector will consume events only is transform is enabled.
            return transformGestureDetector.onTouchEvent(event)
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Touch events are dispatched to the child views and the parent view from here.
        if (!isTransformEnabled) {
            // If transform is disabled, we have to transform the events dispatched to children.
            ev.transform(transformGestureDetector.touchMatrix)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        var result = false
        canvas.withMatrix(transformGestureDetector.drawMatrix) {
            result = super.drawChild(canvas, child, drawingTime)
        }
        return result
    }

}