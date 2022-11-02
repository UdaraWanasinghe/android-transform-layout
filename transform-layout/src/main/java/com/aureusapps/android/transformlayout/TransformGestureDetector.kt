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

class TransformGestureDetector(
    context: Context,
    private val gestureDetectorListener: TransformGestureDetectorListener
) {

    companion object {
        private const val MIN_FLING_VELOCITY = 50f
        private const val MAX_FLING_VELOCITY = 8000f
    }

    var isScaleEnabled = true
    var isRotateEnabled = true
    var isTranslateEnabled = true
    var isFlingEnabled = true

    private val _drawMatrix = DrawMatrix()
    val drawMatrix: Matrix get() = _drawMatrix.matrix
    val touchMatrix: Matrix get() = _drawMatrix.inverse

    private val touchSlopSquare: Int
    private var downFocusX: Float = 0f
    private var downFocusY: Float = 0f
    private var previousFocusX = 0f
    private var previousFocusY = 0f
    private var previousTouchSpan = 1f
    private val pointerMap = HashMap<Int, Pair<Float, Float>>() // touch event pointers
    private var detectSingleTap = true
    private val velocityTracker = VelocityTracker.obtain()
    private var flingAnimX: FlingAnimation? = null
    private var flingAnimY: FlingAnimation? = null
    private var flagTransformStarted = false

    init {
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop
        touchSlopSquare = touchSlop * touchSlop
    }

    @Suppress("unused")
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
        translation: Pair<Float, Float>? = null,
        pivot: Pair<Float, Float>? = null,
        inform: Boolean = true
    ) {
        if (rotation == null && scaling == null && translation == null) return
        cancelAnims()
        _drawMatrix.mutate { mutableMatrix ->
            mutableMatrix.copy(_drawMatrix)
            if (pivot != null) {
                previousFocusX = pivot.first
                previousFocusY = pivot.second
            }
            if (scaling != null) {
                mutableMatrix.setScale(scaling, previousFocusX, previousFocusY)
            }
            if (rotation != null) {
                mutableMatrix.setRotate(rotation, previousFocusX, previousFocusY)
            }
            if (translation != null) {
                mutableMatrix.setTranslate(translation.first, translation.second)
            }
        }
        if (inform) {
            informTransformUpdated()
        }
    }

    @Suppress("unused")
            /**
             * Copy the given transform matrix to the draw matrix.
             *
             * @param matrix Transform matrix to copy.
             * @param inform Whether to inform listeners about the update.
             */
    fun setTransform(matrix: Matrix, inform: Boolean = true) {
        if (_drawMatrix.matrix == matrix) return
        cancelAnims()
        _drawMatrix.mutate { mutableMatrix ->
            mutableMatrix.copy(matrix)
        }
        if (inform) {
            informTransformUpdated()
        }
    }

    @Suppress("unused")
            /**
             * Concatenate the given transform matrix to the current draw matrix.
             *
             * @param matrix Transform matrix to concatenate.
             * @param inform Whether to inform listeners about the update.
             */
    fun concatTransform(matrix: Matrix, inform: Boolean = true) {
        cancelAnims()
        _drawMatrix.mutate { mutableMatrix ->
            mutableMatrix.postConcat(matrix)
        }
        if (inform) {
            informTransformUpdated()
        }
    }

    @Suppress("unused")
            /**
             * Reset transformation matrix to an identity matrix.
             *
             * @param inform Whether to inform listeners about the update.
             */
    fun resetTransform(inform: Boolean = true) {
        if (_drawMatrix.matrix.isIdentity) return
        cancelAnims()
        _drawMatrix.mutate { mutableMatrix ->
            mutableMatrix.reset()
        }
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
                flagTransformStarted = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val (focusX, focusY) = event.focusPoint()
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = event.touchSpan(focusX, focusY)
                detectSingleTap = false
                event.savePointers()
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
                // transform is only updated if single tap is not detected
                // either single tap is out of the detection range or a timeout
                if (!detectSingleTap) {
                    // if transform start not signaled, signal it
                    if (!flagTransformStarted) {
                        flagTransformStarted = true
                        gestureDetectorListener.onTransformStart(previousFocusX, previousFocusY, _drawMatrix.matrix)
                    }
                    _drawMatrix.mutate { mutableMatrix ->
                        if (isScaleEnabled) {
                            val scaling = touchSpan / previousTouchSpan
                            mutableMatrix.postScale(scaling, focusX, focusY)
                        }
                        if (isRotateEnabled) {
                            val rotation = event.rotation(focusX, focusY)
                            mutableMatrix.postRotate(rotation, focusX, focusY)
                        }
                        if (isTranslateEnabled) {
                            val translationX = focusX - previousFocusX
                            val translationY = focusY - previousFocusY
                            mutableMatrix.postTranslate(translationX, translationY)
                        }
                    }
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
                    // whoever complete last will inform the complete event
                    var flagInformComplete = false
                    val pointerId = event.getPointerId(0)
                    velocityTracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                    val velocityY = velocityTracker.getYVelocity(pointerId)
                    val velocityX = velocityTracker.getXVelocity(pointerId)
                    if (abs(velocityY) > MIN_FLING_VELOCITY || abs(velocityX) > MIN_FLING_VELOCITY) {
                        val valueHolder = FloatValueHolder()
                        // start x direction fling animation
                        flingAnimX = FlingAnimation(valueHolder).apply {
                            setStartVelocity(velocityX)
                            setStartValue(0f)
                            var lastValue = 0f
                            _drawMatrix.mutate { mutableMatrix ->
                                addUpdateListener { _, value, _ ->
                                    val ds = value - lastValue
                                    lastValue = value
                                    mutableMatrix.postTranslate(ds, 0f)
                                    informTransformUpdated()
                                }
                            }
                            addEndListener { _, _, _, _ ->
                                if (flagInformComplete) {
                                    gestureDetectorListener.onTransformComplete(previousFocusX, previousFocusY, _drawMatrix.matrix)
                                } else {
                                    flagInformComplete = true
                                }
                            }
                            start()
                        }
                        // start y direction fling animation
                        flingAnimY = FlingAnimation(valueHolder).apply {
                            setStartVelocity(velocityY)
                            setStartValue(0f)
                            var lastValue = 0f
                            _drawMatrix.mutate { mutableMatrix ->
                                addUpdateListener { _, value, _ ->
                                    val ds = value - lastValue
                                    lastValue = value
                                    mutableMatrix.postTranslate(0f, ds)
                                    informTransformUpdated()
                                }
                            }
                            addEndListener { _, _, _, _ ->
                                if (flagInformComplete) {
                                    gestureDetectorListener.onTransformComplete(previousFocusX, previousFocusY, _drawMatrix.matrix)
                                } else {
                                    flagInformComplete = true
                                }
                            }
                            start()
                        }
                    } else {
                        // fling is enabled, but not enough velocity to start animation
                        if (flagTransformStarted) {
                            gestureDetectorListener.onTransformComplete(previousFocusX, previousFocusY, _drawMatrix.matrix)
                        }
                    }
                } else {
                    // fling is not enabled
                    if (flagTransformStarted) {
                        gestureDetectorListener.onTransformComplete(previousFocusX, previousFocusY, _drawMatrix.matrix)
                    }
                }
            }
        }
        return true
    }

    private fun informTransformUpdated() {
        gestureDetectorListener.onTransformUpdate(previousFocusX, previousFocusY, _drawMatrix.lastMatrix, _drawMatrix.matrix)
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

    private val Float.toDegrees: Float
        get() = this * 180f / Math.PI.toFloat()

    protected fun finalize() {
        // since velocity tracker is a natively allocated object, it should be explicitly released.
        velocityTracker?.recycle()
    }

    private class DrawMatrix {
        val matrix = Matrix()
        val lastMatrix = Matrix()
        val inverse: Matrix
            get() {
                if (matrixChanged) {
                    matrix.invert(invertedMatrix)
                }
                return invertedMatrix
            }
        private val invertedMatrix = Matrix()
        private var matrixChanged = false
        private val mutableMatrix = MutableDrawMatrix()

        fun mutate(block: (MutableDrawMatrix) -> Unit) {
            lastMatrix.set(matrix)
            block(mutableMatrix)
            matrixChanged = true
        }

        inner class MutableDrawMatrix {

            fun copy(m: DrawMatrix) {
                matrix.set(m.matrix)
                matrixChanged = true
            }

            fun copy(m: Matrix) {
                matrix.set(m)
                matrixChanged = true
            }

            fun setScale(s: Float, px: Float, py: Float) {
                matrix.setScale(s, s, px, py)
                matrixChanged = true
            }

            fun setRotate(r: Float, px: Float, py: Float) {
                matrix.setRotate(r, px, py)
                matrixChanged = true
            }

            fun setTranslate(tx: Float, ty: Float) {
                matrix.setTranslate(tx, ty)
                matrixChanged = true
            }

            fun postScale(s: Float, px: Float, py: Float) {
                matrix.postScale(s, s, px, py)
                matrixChanged = true
            }

            fun postRotate(r: Float, px: Float, py: Float) {
                matrix.postRotate(r, px, py)
                matrixChanged = true
            }

            fun postTranslate(tx: Float, ty: Float) {
                matrix.postTranslate(tx, ty)
                matrixChanged = true
            }

            fun postConcat(m: Matrix) {
                matrix.postConcat(m)
                matrixChanged = true
            }

            fun reset() {
                matrix.reset()
                matrixChanged = true
            }

        }

    }

}