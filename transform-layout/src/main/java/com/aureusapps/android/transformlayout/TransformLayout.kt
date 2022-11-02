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
import com.aureusapps.android.extensions.obtainStyledAttributes

@SuppressLint("ClickableViewAccessibility")

class TransformLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.transformLayoutStyle,
    defStyleRes: Int = R.style.TransformLayoutStyle
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    @Suppress("MemberVisibilityCanBePrivate")
    var isScaleEnabled
        get() = transformGestureDetector.isScaleEnabled
        set(value) {
            transformGestureDetector.isScaleEnabled = value
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var isRotationEnabled
        get() = transformGestureDetector.isRotateEnabled
        set(value) {
            transformGestureDetector.isRotateEnabled = value
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var isTranslationEnabled
        get() = transformGestureDetector.isTranslateEnabled
        set(value) {
            transformGestureDetector.isTranslateEnabled = value
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var isFlingEnabled
        get() = transformGestureDetector.isFlingEnabled
        set(value) {
            transformGestureDetector.isFlingEnabled = value
        }

    var isTransformEnabled = false

    private val gestureDetectorListeners = mutableSetOf<TransformGestureDetectorListener>()
    private val gestureDetectorListener = object : TransformGestureDetectorListener {
        override fun onTransformStart(px: Float, py: Float, matrix: Matrix) {
            invalidate()
            gestureDetectorListeners.forEach { it.onTransformStart(px, py, matrix) }
        }

        override fun onTransformUpdate(px: Float, py: Float, oldMatrix: Matrix, newMatrix: Matrix) {
            invalidate()
            gestureDetectorListeners.forEach { it.onTransformUpdate(px, py, oldMatrix, newMatrix) }
        }

        override fun onTransformComplete(px: Float, py: Float, matrix: Matrix) {
            invalidate()
            gestureDetectorListeners.forEach { it.onTransformComplete(px, py, matrix) }
        }

        override fun onSingleTap(px: Float, py: Float) {
            invalidate()
            gestureDetectorListeners.forEach { it.onSingleTap(px, py) }
        }
    }
    private val transformGestureDetector = TransformGestureDetector(context, gestureDetectorListener)

    init {
        obtainStyledAttributes(attrs, R.styleable.TransformLayout, defStyleAttr, defStyleRes).apply {
            isScaleEnabled = getBoolean(R.styleable.TransformLayout_scaleEnabled, true)
            isRotationEnabled = getBoolean(R.styleable.TransformLayout_rotationEnabled, true)
            isTranslationEnabled = getBoolean(R.styleable.TransformLayout_translationEnabled, true)
            isFlingEnabled = getBoolean(R.styleable.TransformLayout_flingEnabled, true)
            isTransformEnabled = getBoolean(R.styleable.TransformLayout_transformEnabled, true)
            recycle()
        }
    }

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

    fun addTransformGestureDetectorListener(listener: TransformGestureDetectorListener) {
        gestureDetectorListeners.add(listener)
    }

    fun removeTransformGestureDetectorListener(listener: TransformGestureDetectorListener) {
        gestureDetectorListeners.remove(listener)
    }

}