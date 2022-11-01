package com.aureusapps.android.transformlayout

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import kotlin.math.abs
import kotlin.math.atan2

@Suppress("MemberVisibilityCanBePrivate", "unused")

class TransformGestureDetector(
    context: Context,
    private val gestureDetectorListener: TransformGestureDetectorListener
) {

    companion object {
        private const val MIN_SCALE = 0.01f
        private const val MAX_SCALE = 100f
        private const val MIN_FLING_VELOCITY = 50f
        private const val MAX_FLING_VELOCITY = 8000f
    }

    var isScaleEnabled = true
    var isRotateEnabled = true
    var isTranslateEnabled = true
    var isFlingEnabled = true

    val drawMatrix: Matrix
        get() {
            tempDrawMatrix.set(_drawMatrix.matrix)
            return tempDrawMatrix
        }

    private val _touchMatrix = Matrix()
    private val _drawMatrix = DrawMatrix()
    private val tempDrawMatrix = Matrix()
    private val pointerMap: HashMap<Int, Position> = HashMap() // touch event pointers

    var previousFocusX = 0f
    var previousFocusY = 0f
    val pivotPoint get() = Position(previousFocusX, previousFocusY)
    private var previousTouchSpan = 1f

    private val velocityTracker = VelocityTracker.obtain()
    private var flingAnimX: FlingAnimation? = null
    private var flingAnimY: FlingAnimation? = null

    private var detectSingleTap: Boolean = true
    private val touchSlopSquare: Int
    private var downFocusX: Float = 0f
    private var downFocusY: Float = 0f

    private val _lastDrawMatrix = DrawMatrix()

    init {
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop
        touchSlopSquare = touchSlop * touchSlop
    }

    /**
     * Update scaling, rotation and translation values.
     *
     * @param scaling Scaling value around the given pivot point or the previous pivot point.
     * @param rotation Rotation value around the given pivot point or the previous pivot point.
     * @param translation Translation value to set.
     * @param pivot Point to scale and rotate around.
     * @param inform Whether to inform listeners about the update.
     */
    fun setTransform(
        scaling: Float? = null,
        rotation: Float? = null,
        translation: Position? = null,
        pivot: Position? = null,
        inform: Boolean = true
    ) {
        cancelAnims()
        _lastDrawMatrix.set(_drawMatrix)
        if (pivot != null) {
            previousFocusX = pivot.first
            previousFocusY = pivot.second
        }
        if (scaling != null) {
            _drawMatrix.setScale(scaling, previousFocusX, previousFocusY)
        }
        if (rotation != null) {
            _drawMatrix.setRotate(rotation, previousFocusX, previousFocusY)
        }
        if (translation != null) {
            _drawMatrix.setTranslate(translation.first, translation.second)
        }
        if (inform) {
            informTransformUpdated()
        }
    }

    /**
     * Copy the given transform matrix to the draw matrix.
     *
     * @param matrix Transform matrix to copy.
     * @param inform Whether to inform listeners about the update.
     */
    fun setTransform(matrix: Matrix, inform: Boolean = true) {
        cancelAnims()
        _lastDrawMatrix.set(_drawMatrix)
        _drawMatrix.set(matrix)
        if (inform) {
            informTransformUpdated()
        }
    }

    /**
     * Concatenate the given transform matrix to the current draw matrix.
     *
     * @param matrix Transform matrix to concatenate.
     * @param inform Whether to inform listeners about the update.
     */
    fun concatTransform(matrix: Matrix, inform: Boolean = true) {
        cancelAnims()
        _lastDrawMatrix.set(matrix)
        _drawMatrix.postConcat(matrix)
        if (inform) {
            informTransformUpdated()
        }
    }

    /**
     * Reset transformation matrix to an identity matrix.
     *
     * @param inform Whether to inform listeners about the update.
     */
    fun resetTransform(inform: Boolean = true) {
        cancelAnims()
        _lastDrawMatrix.set(_drawMatrix)
        _drawMatrix.reset()
        if (inform) {
            informTransformUpdated()
        }
    }

    /**
     * Call this on touch event to handle gesture detection.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnims()
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                val (focusX, focusY) = event.focusPoint()
                downFocusX = focusX
                downFocusY = focusY
                previousFocusX = focusX
                previousFocusY = focusY
                detectSingleTap = true
                event.savePointers()
                gestureDetectorListener.onTransformStart(previousFocusX, previousFocusY, _drawMatrix.matrix)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                detectSingleTap = false
                event.updateTransformParams()
            }
            MotionEvent.ACTION_MOVE -> {
                val (focusX, focusY) = event.focusPoint()
                // update single tap flag
                if (detectSingleTap) {
                    val dx = focusX - downFocusX
                    val dy = focusY - downFocusY
                    val ds = dx * dx + dy * dy
                    if (ds > touchSlopSquare) {
                        detectSingleTap = false
                    } else {
                        val dt = event.eventTime - event.downTime
                        if (dt > ViewConfiguration.getTapTimeout()) {
                            detectSingleTap = false
                        }
                    }
                }
                // update velocity tracker
                velocityTracker?.addMovement(event)
                // update transform
                val touchSpan = event.touchSpan(focusX, focusY)
                if (isScaleEnabled) {
                    val scaling = touchSpan / previousTouchSpan
                    _drawMatrix.postScale(scaling, focusX, focusY)
                }
                if (isRotateEnabled) {
                    val rotation = event.rotation(focusX, focusY)
                    _drawMatrix.postRotate(rotation, focusX, focusY)
                }
                if (isTranslateEnabled) {
                    val translationX = focusX - previousFocusX
                    val translationY = focusY - previousFocusY
                    _drawMatrix.postTranslate(translationX, translationY)
                }
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = touchSpan
                event.savePointers()
                // inform listeners
                informTransformUpdated()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val (focusX, focusY) = event.focusPoint()
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = event.touchSpan(focusX, focusY)
                detectSingleTap = false
                // Check the dot product of current velocities.
                // If the pointer that left was opposing another velocity vector, clear.
                if (isFlingEnabled) {
                    velocityTracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                    val upIndex: Int = event.actionIndex
                    val id1: Int = event.getPointerId(upIndex)
                    val x1 = velocityTracker.getXVelocity(id1)
                    val y1 = velocityTracker.getYVelocity(id1)
                    for (i in 0 until event.pointerCount) {
                        if (i == upIndex) continue
                        val id2 = event.getPointerId(i)
                        val x = x1 * velocityTracker.getXVelocity(id2)
                        val y = y1 * velocityTracker.getYVelocity(id2)
                        val dot = x + y
                        if (dot < 0) {
                            velocityTracker.clear()
                            break
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val (focusX, focusY) = event.focusPoint()
                velocityTracker.addMovement(event)
                if (detectSingleTap) {
                    val dt = event.eventTime - event.downTime
                    if (dt < ViewConfiguration.getTapTimeout()) {
                        gestureDetectorListener.onSingleTap(event.x, event.y)
                        return true
                    }
                }
                // do fling animation
                if (isFlingEnabled) {
                    var flingAboutToComplete = false
                    val pointerId: Int = event.getPointerId(0)
                    velocityTracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                    val velocityY: Float = velocityTracker.getYVelocity(pointerId)
                    val velocityX: Float = velocityTracker.getXVelocity(pointerId)
                    if (abs(velocityY) > MIN_FLING_VELOCITY || abs(velocityX) > MIN_FLING_VELOCITY) {
                        val valueHolder = FloatValueHolder()
                        flingAnimX = FlingAnimation(valueHolder).apply {
                            setStartVelocity(velocityX)
                            setStartValue(0f)
                            var lastValue = 0f
                            addUpdateListener { _, value, _ ->
                                val ds = value - lastValue
                                lastValue = value
                                _drawMatrix.postTranslate(ds, 0f)
                                informTransformUpdated()
                            }
                            addEndListener { _, _, _, _ ->
                                if (flingAboutToComplete) {
                                    gestureDetectorListener.onTransformComplete(focusX, focusY, _drawMatrix.matrix)
                                } else {
                                    flingAboutToComplete = true
                                }
                            }
                            start()
                        }
                        flingAnimY = FlingAnimation(valueHolder).apply {
                            setStartVelocity(velocityY)
                            setStartValue(0f)
                            var lastValue = 0f
                            addUpdateListener { _, value, _ ->
                                val ds = value - lastValue
                                lastValue = value
                                _drawMatrix.postTranslate(0f, ds)
                                informTransformUpdated()
                            }
                            addEndListener { _, _, _, _ ->
                                if (flingAboutToComplete) {
                                    gestureDetectorListener.onTransformComplete(focusX, focusY, _drawMatrix.matrix)
                                } else {
                                    flingAboutToComplete = true
                                }
                            }
                            start()
                        }
                    }
                    velocityTracker.clear()
                } else {
                    gestureDetectorListener.onTransformComplete(focusX, focusY, _drawMatrix.matrix)
                }
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = event.touchSpan(focusX, focusY)
            }
        }
        return true
    }

    private fun informTransformUpdated() {
        gestureDetectorListener.onTransformUpdate(previousFocusX, previousFocusY, _lastDrawMatrix.matrix, _drawMatrix.matrix)
    }

    private fun MotionEvent.updateTransformParams() {
        val (focusX, focusY) = focusPoint()
        previousFocusX = focusX
        previousFocusY = focusY
        previousTouchSpan = touchSpan(focusX, focusY)
        savePointers()
    }

    private fun MotionEvent.focusPoint(): Pair<Float, Float> {
        val upIndex = if (actionMasked == MotionEvent.ACTION_POINTER_UP) actionIndex else -1
        var sumX = 0f
        var sumY = 0f
        var sumCount = 0
        for (pointerIndex in 0 until pointerCount) {
            if (pointerIndex == upIndex) continue
            sumX += getX(pointerIndex)
            sumY += getY(pointerIndex)
            sumCount++
        }
        val focusX = sumX / sumCount
        val focusY = sumY / sumCount
        return focusX to focusY
    }

    private fun MotionEvent.touchSpan(
        currentFocusX: Float,
        currentFocusY: Float
    ): Float {
        // touch span is the average distance between all active pointers and the current focus point
        // distance can be calculated using the Pythagoras theorem: z = sqrt(x^2 + y^2)
        // but to reduce computation cost, we use z = x + y instead
        var spanSumX = 0f
        var spanSumY = 0f
        var sumCount = 0
        val upIndex = if (actionMasked == MotionEvent.ACTION_POINTER_UP) actionIndex else -1
        for (pointerIndex in 0 until pointerCount) {
            if (pointerIndex == upIndex) continue
            spanSumX += abs(currentFocusX - getX(pointerIndex))
            spanSumY += abs(currentFocusY - getY(pointerIndex))
            sumCount++
        }
        if (sumCount > 1) {
            val spanX = spanSumX / sumCount
            val spanY = spanSumY / sumCount
            return spanX + spanY
        }
        // if there is only one pointer, return the previous touch span
        return previousTouchSpan
    }

    private fun scaling(currentTouchSpan: Float): Float {
        // scaling is calculated relative to the previous touch span
        return currentTouchSpan / previousTouchSpan
    }

    /**
     * Returns rotation around the focus point in degrees compared to previous pointer positions.
     */
    private fun MotionEvent.rotation(
        currentFocusX: Float,
        currentFocusY: Float
    ): Float {
        var rotationSum = 0f
        var weightSum = 0f
        for (pointerIndex in 0 until pointerCount) {
            val pointerId = getPointerId(pointerIndex)
            val x1 = getX(pointerIndex)
            val y1 = getY(pointerIndex)
            val (x2, y2) = pointerMap[pointerId] ?: continue
            val dx1 = x1 - currentFocusX
            val dy1 = y1 - currentFocusY
            val dx2 = x2 - currentFocusX
            val dy2 = y2 - currentFocusY
            // dot product is proportional to the cosine of the angle
            // the determinant is proportional to its sine
            // sign of the rotation tells if it is clockwise or counter-clockwise
            val dot = dx1 * dx2 + dy1 * dy2
            val det = dy1 * dx2 - dx1 * dy2
            val rotation = atan2(det, dot)
            val weight = abs(dx1) + abs(dy1)
            rotationSum += rotation * weight
            weightSum += weight
        }
        if (weightSum > 0f) {
            val rotation = rotationSum / weightSum
            return rotation.toDegrees
        }
        return 0f
    }

    private fun translation(
        currentFocusX: Float,
        currentFocusY: Float
    ): Pair<Float, Float> {
        // translation is the difference between current focus point and previous focus point
        return (currentFocusX - previousFocusX) to (currentFocusY - previousFocusY)
    }

    private fun MotionEvent.savePointers() {
        pointerMap.clear()
        // get the pointer id if this event is a pointer up event
        val upIndex = if (actionMasked == MotionEvent.ACTION_POINTER_UP) actionIndex else -1
        // save all active pointers to find the pivot point
        for (pointerIndex in 0 until pointerCount) {
            // skip the pointer that is up
            if (pointerIndex == upIndex) continue
            val id = getPointerId(pointerIndex)
            val x = getX(pointerIndex)
            val y = getY(pointerIndex)
            pointerMap[id] = x to y
        }
    }

    private fun cancelAnims() {
        flingAnimX?.cancel()
        flingAnimY?.cancel()
        flingAnimX = null
        flingAnimY = null
    }

    private val Float.toRadians: Float
        get() = this * Math.PI.toFloat() / 180f

    private val Float.toDegrees: Float
        get() = this * 180f / Math.PI.toFloat()

    protected fun finalize() {
        // since velocity tracker is a natively allocated object, it should be explicitly released.
        velocityTracker?.recycle()
    }

    private class DrawMatrix {
        val matrix = Matrix()
        val inverse: Matrix
            get() {
                if (changed) {
                    matrix.invert(tempMatrix)
                }
                return tempMatrix
            }
        private val tempMatrix = Matrix()
        private var changed = false

        fun set(m: DrawMatrix) {
            matrix.set(m.matrix)
            changed = true
        }

        fun set(m: Matrix) {
            matrix.set(m)
            changed = true
        }

        fun setScale(s: Float, px: Float, py: Float) {
            matrix.setScale(s, s, px, py)
            changed = true
        }

        fun setRotate(r: Float, px: Float, py: Float) {
            matrix.setRotate(r, px, py)
            changed = true
        }

        fun setTranslate(tx: Float, ty: Float) {
            matrix.setTranslate(tx, ty)
            changed = true
        }

        fun postScale(s: Float, px: Float, py: Float) {
            matrix.postScale(s, s, px, py)
            changed = true
        }

        fun postRotate(r: Float, px: Float, py: Float) {
            matrix.postRotate(r, px, py)
            changed = true
        }

        fun postTranslate(tx: Float, ty: Float) {
            matrix.postTranslate(tx, ty)
            changed = true
        }

        fun postConcat(m: Matrix) {
            matrix.postConcat(m)
            changed = true
        }

        fun reset() {
            matrix.reset()
            changed = true
        }

    }

}