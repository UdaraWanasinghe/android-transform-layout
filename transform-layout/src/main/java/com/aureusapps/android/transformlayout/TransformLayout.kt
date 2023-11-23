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

open class TransformLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.transformLayoutStyle,
    defStyleRes: Int = R.style.TransformLayoutStyle
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), Transformable {

    override var isScaleEnabled
        get() = gestureDetector.isScaleEnabled
        set(value) {
            gestureDetector.isScaleEnabled = value
        }

    override var isRotateEnabled
        get() = gestureDetector.isRotateEnabled
        set(value) {
            gestureDetector.isRotateEnabled = value
        }

    override var isTranslateEnabled
        get() = gestureDetector.isTranslateEnabled
        set(value) {
            gestureDetector.isTranslateEnabled = value
        }

    override var isFlingEnabled
        get() = gestureDetector.isFlingEnabled
        set(value) {
            gestureDetector.isFlingEnabled = value
        }

    override val pivotPoint: Pair<Float, Float>
        get() = gestureDetector.pivotPoint

    var isTransformEnabled = false

    private val gestureDetectorListeners = mutableSetOf<TransformGestureDetectorListener>()
    private val gestureDetectorListener = object : TransformGestureDetectorListener {

        override fun onTransformStart(
            px: Float,
            py: Float,
            matrix: Matrix,
            gestureDetector: TransformGestureDetector
        ) {
            super.onTransformStart(px, py, matrix, gestureDetector)
            invalidate()
            gestureDetectorListeners.forEach {
                it.onTransformStart(
                    px,
                    py,
                    matrix,
                    gestureDetector
                )
            }
        }

        override fun onTransformUpdate(
            px: Float,
            py: Float,
            oldMatrix: Matrix,
            newMatrix: Matrix,
            gestureDetector: TransformGestureDetector
        ) {
            super.onTransformUpdate(px, py, oldMatrix, newMatrix, gestureDetector)
            invalidate()
            gestureDetectorListeners.forEach {
                it.onTransformUpdate(px, py, oldMatrix, newMatrix, gestureDetector)
            }
        }

        override fun onTransformComplete(
            px: Float,
            py: Float,
            matrix: Matrix,
            gestureDetector: TransformGestureDetector
        ) {
            super.onTransformComplete(px, py, matrix, gestureDetector)
            invalidate()
            gestureDetectorListeners.forEach {
                it.onTransformComplete(px, py, matrix, gestureDetector)
            }
        }

        override fun onSingleTap(px: Float, py: Float, gestureDetector: TransformGestureDetector) {
            super.onSingleTap(px, py, gestureDetector)
            invalidate()
            gestureDetectorListeners.forEach { it.onSingleTap(px, py, gestureDetector) }
        }
    }
    private val gestureDetector = TransformGestureDetector(context).apply {
        setGestureDetectorListener(gestureDetectorListener)
    }

    override val transformMatrix: Matrix get() = gestureDetector.transformMatrix

    override val inverseTransformMatrix: Matrix get() = gestureDetector.inverseTransformMatrix

    init {
        obtainStyledAttributes(
            attrs,
            R.styleable.TransformLayout,
            defStyleAttr,
            defStyleRes
        ).apply {
            isScaleEnabled = getBoolean(R.styleable.TransformLayout_scaleEnabled, true)
            isRotateEnabled = getBoolean(R.styleable.TransformLayout_rotateEnabled, true)
            isTranslateEnabled = getBoolean(R.styleable.TransformLayout_translateEnabled, true)
            isFlingEnabled = getBoolean(R.styleable.TransformLayout_flingEnabled, true)
            isTransformEnabled = getBoolean(R.styleable.TransformLayout_transformEnabled, true)
            recycle()
        }
    }

    override fun setTransform(matrix: Matrix, notify: Boolean): Boolean {
        return gestureDetector.setTransform(matrix, notify)
    }

    override fun setTransform(values: FloatArray, notify: Boolean): Boolean {
        return gestureDetector.setTransform(values, notify)
    }

    override fun setTransform(
        scaling: Float?,
        rotation: Float?,
        translation: Pair<Float, Float>?,
        pivot: Pair<Float, Float>?,
        notify: Boolean
    ): Boolean {
        return gestureDetector.setTransform(scaling, rotation, translation, pivot, notify)
    }

    override fun concatTransform(matrix: Matrix, notify: Boolean) {
        gestureDetector.concatTransform(matrix, notify)
    }

    override fun concatTransform(values: FloatArray, notify: Boolean) {
        gestureDetector.concatTransform(values, notify)
    }

    override fun concatTransform(
        scaling: Float?,
        rotation: Float?,
        translation: Pair<Float, Float>?,
        pivot: Pair<Float, Float>?,
        notify: Boolean
    ) {
        gestureDetector.concatTransform(scaling, rotation, translation, pivot, notify)
    }

    override fun resetTransform(notify: Boolean): Boolean {
        return gestureDetector.resetTransform(notify)
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
            return gestureDetector.onTouchEvent(event)
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Touch events are dispatched to the child views and the parent view from here.
        if (!isTransformEnabled) {
            // If transform is disabled, we have to transform the events dispatched to children.
            ev.transform(gestureDetector.inverseTransformMatrix)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long
    ): Boolean {
        var result = false
        canvas.withMatrix(gestureDetector.transformMatrix) {
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