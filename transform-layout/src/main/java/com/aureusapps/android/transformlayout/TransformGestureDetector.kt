package com.aureusapps.android.transformlayout

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.core.graphics.values
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import com.aureusapps.android.transformlayout.extensions.focusPoint
import kotlin.math.abs
import kotlin.math.atan2

class TransformGestureDetector : Transformable {

    companion object {
        private const val MIN_FLING_VELOCITY = 50f
        private const val MAX_FLING_VELOCITY = 8000f
    }

    override var isTranslateEnabled = true
    override var isScaleEnabled = true
    override var isRotateEnabled = true
    override var isFlingEnabled = true

    private val _transformMatrix = TransformMatrix()
    private val touchDownTransform = Matrix()
    override val transformMatrix: Matrix
        get() = _transformMatrix.matrix
    override val inverseTransformMatrix: Matrix
        get() = _transformMatrix.inverse
    override val pivotPoint: Pair<Float, Float>
        get() = previousFocusX to previousFocusY

    private val touchSlopSquare: Int
    private var downFocusX: Float = 0f
    private var downFocusY: Float = 0f
    private var previousFocusX = 0f
    private var previousFocusY = 0f
    private var previousTouchSpan = 1f
    private val pointerMap = HashMap<Int, Pair<Float, Float>>() // touch event pointers
    private var detectSingleTap = true
    private var detectLongPress = true
    private var velocityTracker: VelocityTracker? = null
    private var flingAnimX: FlingAnimation? = null
    private var flingAnimY: FlingAnimation? = null
    private var flagTransformStarted = false
    private var gestureDetectorListener: TransformGestureDetectorListener? = null

    /**
     * Call [ViewConfigurationCompatExtended.setTouchSlop] to update the touch slop value before
     * creating an instance of [TransformGestureDetector].
     */
    constructor(touchSlop: Int = ViewConfigurationCompatExtended.getTouchSlop()) {
        touchSlopSquare = touchSlop * touchSlop
    }

    constructor(context: Context) {
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop
        touchSlopSquare = touchSlop * touchSlop
    }

    fun setGestureDetectorListener(listener: TransformGestureDetectorListener?) {
        gestureDetectorListener = listener
    }

    override fun setTransform(
        scaling: Float?,
        rotation: Float?,
        translation: Pair<Float, Float>?,
        pivot: Pair<Float, Float>?,
        notify: Boolean
    ): Boolean {
        if (rotation == null && scaling == null && translation == null) return false
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
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
        if (notify) {
            informTransformUpdated()
        }
        return true
    }

    override fun setTransform(matrix: Matrix, notify: Boolean): Boolean {
        if (_transformMatrix.matrix == matrix) return false
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.copy(matrix)
        }
        if (notify) {
            informTransformUpdated()
        }
        return true
    }

    override fun setTransform(values: FloatArray, notify: Boolean): Boolean {
        if (_transformMatrix.matrix.values().contentEquals(values)) return false
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.copyValues(values)
        }
        if (notify) {
            informTransformUpdated()
        }
        return true
    }

    override fun concatTransform(
        scaling: Float?,
        rotation: Float?,
        translation: Pair<Float, Float>?,
        pivot: Pair<Float, Float>?,
        notify: Boolean
    ) {
        if (rotation == null && scaling == null && translation == null) return
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            if (pivot != null) {
                previousFocusX = pivot.first
                previousFocusY = pivot.second
            }
            if (scaling != null) {
                mutableMatrix.postScale(scaling, previousFocusX, previousFocusY)
            }
            if (rotation != null) {
                mutableMatrix.postRotate(rotation, previousFocusX, previousFocusY)
            }
            if (translation != null) {
                mutableMatrix.postTranslate(translation.first, translation.second)
            }
        }
        if (notify) {
            informTransformUpdated()
        }
    }

    override fun concatTransform(matrix: Matrix, notify: Boolean) {
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.postConcat(matrix)
        }
        if (notify) {
            informTransformUpdated()
        }
    }

    override fun concatTransform(values: FloatArray, notify: Boolean) {
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.postConcat(values)
        }
        if (notify) {
            informTransformUpdated()
        }
    }

    override fun resetTransform(notify: Boolean): Boolean {
        if (_transformMatrix.isIdentity) return false
        cancelAnims()
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.reset()
        }
        if (notify) {
            informTransformUpdated()
        }
        return true
    }

    /**
     * Call this on touch event to handle gesture detection.
     */
    @Suppress("SameReturnValue")
    fun onTouchEvent(event: MotionEvent): Boolean {
        val velocityTracker = getVelocityTracker()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTransform.set(_transformMatrix.matrix)
                cancelAnims()
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                val (focusX, focusY) = event.focusPoint()
                downFocusX = focusX
                downFocusY = focusY
                previousFocusX = focusX
                previousFocusY = focusY
                detectSingleTap = true
                detectLongPress = true
                event.savePointers()
                flagTransformStarted = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val (focusX, focusY) = event.focusPoint()
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = event.touchSpan(focusX, focusY)
                detectSingleTap = false
                detectLongPress = false
                event.savePointers()
            }

            MotionEvent.ACTION_MOVE -> {
                val (focusX, focusY) = event.focusPoint()
                // update single tap flag
                if (detectLongPress) {
                    val dx = focusX - downFocusX
                    val dy = focusY - downFocusY
                    val ds = dx * dx + dy * dy
                    if (ds > touchSlopSquare) {
                        detectSingleTap = false
                        detectLongPress = false
                    }
                    if (detectSingleTap) {
                        val dt = event.eventTime - event.downTime
                        if (dt > ViewConfiguration.getTapTimeout()) {
                            detectSingleTap = false
                        }
                    }
                }
                // update velocity tracker
                velocityTracker.addMovement(event)
                // update transform
                val touchSpan = event.touchSpan(focusX, focusY)
                // transform is only updated if single tap is not detected
                // either single tap is out of the detection range or a timeout
                if (!detectSingleTap) {
                    // if transform start not signaled, signal it
                    if (!flagTransformStarted) {
                        flagTransformStarted = true
                        gestureDetectorListener?.onTransformStart(
                            previousFocusX,
                            previousFocusY,
                            touchDownTransform,
                            this
                        )
                    }
                    _transformMatrix.mutate { mutableMatrix ->
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
                if (flagTransformStarted) {
                    informTransformUpdated()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val (focusX, focusY) = event.focusPoint()
                previousFocusX = focusX
                previousFocusY = focusY
                previousTouchSpan = event.touchSpan(focusX, focusY)
                detectSingleTap = false
                detectLongPress = false
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
                        if (flagTransformStarted) {
                            setTransformToTouchDownState()
                        }
                        val handled =
                            gestureDetectorListener?.onSingleTap(event.x, event.y, this) ?: false
                        if (handled) {
                            return true
                        }
                    }
                }
                if (detectLongPress) {
                    val dt = event.eventTime - event.downTime
                    if (dt > ViewConfiguration.getLongPressTimeout()) {
                        if (flagTransformStarted) {
                            setTransformToTouchDownState()
                        }
                        val handled =
                            gestureDetectorListener?.onLongPress(event.x, event.y, this) ?: false
                        if (handled) {
                            return true
                        }
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
                            _transformMatrix.mutate { mutableMatrix ->
                                addUpdateListener { _, value, _ ->
                                    val ds = value - lastValue
                                    lastValue = value
                                    mutableMatrix.postTranslate(ds, 0f)
                                    informTransformUpdated()
                                }
                            }
                            addEndListener { _, _, _, _ ->
                                if (flagInformComplete) {
                                    gestureDetectorListener?.onTransformComplete(
                                        previousFocusX,
                                        previousFocusY,
                                        _transformMatrix.matrix,
                                        this@TransformGestureDetector
                                    )
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
                            _transformMatrix.mutate { mutableMatrix ->
                                addUpdateListener { _, value, _ ->
                                    val ds = value - lastValue
                                    lastValue = value
                                    mutableMatrix.postTranslate(0f, ds)
                                    informTransformUpdated()
                                }
                            }
                            addEndListener { _, _, _, _ ->
                                if (flagInformComplete) {
                                    gestureDetectorListener?.onTransformComplete(
                                        previousFocusX,
                                        previousFocusY,
                                        _transformMatrix.matrix,
                                        this@TransformGestureDetector
                                    )
                                } else {
                                    flagInformComplete = true
                                }
                            }
                            start()
                        }
                    } else {
                        // fling is enabled, but not enough velocity to start animation
                        if (flagTransformStarted) {
                            gestureDetectorListener?.onTransformComplete(
                                previousFocusX,
                                previousFocusY,
                                _transformMatrix.matrix,
                                this
                            )
                        }
                    }
                } else {
                    // fling is not enabled
                    if (flagTransformStarted) {
                        gestureDetectorListener?.onTransformComplete(
                            previousFocusX,
                            previousFocusY,
                            _transformMatrix.matrix,
                            this
                        )
                    }
                }
            }
        }
        return true
    }

    private fun getVelocityTracker(): VelocityTracker {
        return velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
    }

    private fun informTransformUpdated() {
        gestureDetectorListener?.onTransformUpdate(
            previousFocusX,
            previousFocusY,
            _transformMatrix.lastMatrix,
            _transformMatrix.matrix,
            this
        )
    }

    private fun setTransformToTouchDownState() {
        _transformMatrix.mutate { mutableMatrix ->
            mutableMatrix.set(touchDownTransform)
        }
        informTransformUpdated()
    }

    private fun MotionEvent.touchSpan(
        currentFocusX: Float, currentFocusY: Float
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
        currentFocusX: Float, currentFocusY: Float
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

    private class TransformMatrix {
        val matrix = Matrix()
        val lastMatrix = Matrix()
        val inverse: Matrix
            get() {
                if (matrixChanged) {
                    matrixChanged = false
                    matrix.invert(invertedMatrix)
                }
                return invertedMatrix
            }
        val isIdentity get() = matrix.isIdentity
        private val invertedMatrix = Matrix()
        private var matrixChanged = false
        private val mutableMatrix = MutableTransformMatrix()

        fun mutate(block: (MutableTransformMatrix) -> Unit) {
            lastMatrix.set(matrix)
            block(mutableMatrix)
            matrixChanged = true
        }

        inner class MutableTransformMatrix {

            private val tempMatrix = Matrix()

            fun copy(m: Matrix) {
                matrix.set(m)
                matrixChanged = true
            }

            fun copyValues(values: FloatArray) {
                matrix.setValues(values)
                matrixChanged = true
            }

            fun set(m: Matrix) {
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

            fun postConcat(values: FloatArray) {
                tempMatrix.setValues(values)
                matrix.postConcat(tempMatrix)
                matrixChanged = true
            }

            fun reset() {
                matrix.reset()
                matrixChanged = true
            }

        }

    }

    fun release() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

}