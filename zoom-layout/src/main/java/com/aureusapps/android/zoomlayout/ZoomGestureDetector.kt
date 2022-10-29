package com.aureusapps.android.zoomlayout

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.core.math.MathUtils
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import com.aureusapps.android.extensions.rotation
import com.aureusapps.android.extensions.scaling
import com.aureusapps.android.extensions.translation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@Suppress("MemberVisibilityCanBePrivate", "unused")

class ZoomGestureDetector(
    context: Context,
    private val gestureDetectorListener: ZoomGestureDetectorListener
) {

    companion object {
        private const val MIN_SCALE = 0.01f
        private const val MAX_SCALE = 100f
        private const val MIN_FLING_VELOCITY = 50f
        private const val MAX_FLING_VELOCITY = 8000f
        private const val SINGLE_TAP_TIMEOUT = 400
    }

    var isZoomEnabled = false
    var isScaleEnabled = true
    var isRotationEnabled = true
    var isTranslationEnabled = true
    var isFlingEnabled = true

    val scaling: Float get() = _drawMatrix.scaling
    val rotation: Float get() = _drawMatrix.rotation
    val translation: Position get() = _drawMatrix.translation

    val drawMatrix: Matrix get() = _drawMatrix
    val touchMatrix: Matrix
        get() {
            if (drawMatrixChanged) {
                _drawMatrix.invert(_touchMatrix)
                drawMatrixChanged = false
            }
            return _touchMatrix
        }

    private val _drawMatrix: Matrix = Matrix()
    private val pointerMap: HashMap<Int, Position> = HashMap() // touch event pointers

    private var translationX = 0f
    private var translationY = 0f
    private var _scaling = 1f
    private var pivotX = 0f
    private var pivotY = 0f
    private var _rotation = 0f

    private var previousFocusX = 0f
    private var previousFocusY = 0f
    private var previousTouchSpan = 1f

    private var velocityTracker: VelocityTracker? = null
    private var flingAnimX: FlingAnimation? = null
    private var flingAnimY: FlingAnimation? = null

    private var alwaysInTapRegion: Boolean = true
    private val touchSlopSquare: Int
    private var downFocusX: Float = 0f
    private var downFocusY: Float = 0f

    private val _touchMatrix: Matrix = Matrix()
    private val oldDrawMatrix: Matrix = Matrix()
    private var drawMatrixChanged: Boolean = true

    init {
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop
        touchSlopSquare = touchSlop * touchSlop
    }

    fun setZoom(
        scaling: Float = _drawMatrix.scaling,
        rotation: Float = _drawMatrix.rotation,
        translation: Position = _drawMatrix.translation,
        informUpdated: Boolean = true
    ) {
        cancelAnims()
        _scaling = scaling
        _rotation = rotation
        val (translationX, translationY) = translation
        this.translationX = translationX
        this.translationY = translationY
        pivotX = 0f
        pivotY = 0f
        updateDrawMatrix()
        if (informUpdated) {
            informUpdated()
        }
    }

    fun setZoom(matrix: Matrix, informUpdated: Boolean = true) {
        cancelAnims()
        _scaling = matrix.scaling
        _rotation = matrix.rotation
        val (translationX, translationY) = matrix.translation
        this.translationX = translationX
        this.translationY = translationY
        pivotX = 0f
        pivotY = 0f
        updateDrawMatrix(matrix)
        if (informUpdated) {
            informUpdated()
        }
    }

    fun concatZoom(matrix: Matrix, informUpdated: Boolean = true) {
        cancelAnims()
        _drawMatrix.postConcat(matrix)
        setZoom(_drawMatrix, informUpdated)
    }

    fun resetZoom(informUpdated: Boolean = true) {
        cancelAnims()
        translationX = 0f
        translationY = 0f
        _scaling = 1f
        pivotX = 0f
        pivotY = 0f
        _rotation = 0f
        _drawMatrix.reset()
        if (informUpdated) {
            informUpdated()
        }
    }

    fun scaleUp(stepSize: Float = 0.2f) {
        _scaling += stepSize
        updateDrawMatrix()
        informUpdated()
    }

    fun scaleDown(stepSize: Float = 0.2f) {
        _scaling -= stepSize
        updateDrawMatrix()
        informUpdated()
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        // update velocity tracker
        if (isFlingEnabled) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain()
            }
            velocityTracker?.addMovement(event)
        }
        // handle touch events
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downFocusX = event.x
                downFocusY = event.y
                // update focus point
                previousFocusX = downFocusX
                previousFocusY = downFocusY
                event.savePointers()
                // cancel ongoing fling animations
                cancelAnims()
                // tap
                alwaysInTapRegion = true
                gestureDetectorListener.onZoomStart(downFocusX to downFocusY, _drawMatrix)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                updateTouchParameters(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Check the dot product of current velocities.
                // If the pointer that left was opposing another velocity vector, clear.
                if (isFlingEnabled) {
                    velocityTracker?.let { tracker ->
                        tracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                        val upIndex: Int = event.actionIndex
                        val id1: Int = event.getPointerId(upIndex)
                        val x1 = tracker.getXVelocity(id1)
                        val y1 = tracker.getYVelocity(id1)
                        for (i in 0 until event.pointerCount) {
                            if (i == upIndex) continue
                            val id2: Int = event.getPointerId(i)
                            val x = x1 * tracker.getXVelocity(id2)
                            val y = y1 * tracker.getYVelocity(id2)
                            val dot = x + y
                            if (dot < 0) {
                                tracker.clear()
                                break
                            }
                        }
                    }
                }
                updateTouchParameters(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val (focusX, focusY) = event.focalPoint()
                if (alwaysInTapRegion) {
                    val deltaX = focusX - downFocusX
                    val deltaY = focusY - downFocusY
                    val distance = (deltaX * deltaX) + (deltaY * deltaY)
                    if (distance > touchSlopSquare) {
                        alwaysInTapRegion = false
                    } else {
                        val deltaTime = event.eventTime - event.downTime
                        if (deltaTime > SINGLE_TAP_TIMEOUT) {
                            alwaysInTapRegion = false
                        }
                    }
                }
                if (!alwaysInTapRegion) {
                    if (event.pointerCount > 1) {
                        if (isScaleEnabled) {
                            val touchSpan = event.touchSpan(focusX, focusY)
                            _scaling *= scaling(touchSpan)
                            _scaling = MathUtils.clamp(_scaling, MIN_SCALE, MAX_SCALE)
                            previousTouchSpan = touchSpan
                        }
                        if (isRotationEnabled) {
                            _rotation += event.rotation(focusX, focusY)
                        }
                        if (isTranslationEnabled) {
                            val (translationX, translationY) = translation(focusX, focusY)
                            this.translationX += translationX
                            this.translationY += translationY
                        }
                    } else {
                        if (isTranslationEnabled) {
                            val (translationX, translationY) = translation(focusX, focusY)
                            this.translationX += translationX
                            this.translationY += translationY
                        }
                    }
                    previousFocusX = focusX
                    previousFocusY = focusY
                    updateDrawMatrix()
                    event.savePointers()
                    informUpdated()
                }
            }
            MotionEvent.ACTION_UP -> {
                val focusX = event.x
                val focusY = event.y
                // single tap
                val deltaTime = event.eventTime - event.downTime
                if (alwaysInTapRegion && deltaTime < SINGLE_TAP_TIMEOUT) {
                    gestureDetectorListener.onSingleTap(focusX to focusY)
                } else {
                    // do fling animation
                    if (isFlingEnabled) {
                        var flingAboutToComplete = false
                        velocityTracker?.let { tracker ->
                            val pointerId: Int = event.getPointerId(0)
                            tracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY)
                            val velocityY: Float = tracker.getYVelocity(pointerId)
                            val velocityX: Float = tracker.getXVelocity(pointerId)
                            if (abs(velocityY) > MIN_FLING_VELOCITY || abs(velocityX) > MIN_FLING_VELOCITY) {
                                val translateX = translationX
                                val translateY = translationY
                                val valueHolder = FloatValueHolder()
                                flingAnimX = FlingAnimation(valueHolder).apply {
                                    setStartVelocity(velocityX)
                                    setStartValue(0f)
                                    addUpdateListener { _, value, _ ->
                                        translationX = translateX + value
                                        updateDrawMatrix()
                                        informUpdated()
                                    }
                                    addEndListener { _, _, _, _ ->
                                        if (flingAboutToComplete) {
                                            gestureDetectorListener.onZoomComplete(focusX to focusY, _drawMatrix)
                                        } else {
                                            flingAboutToComplete = true
                                        }
                                    }
                                    start()
                                }
                                flingAnimY = FlingAnimation(valueHolder).apply {
                                    setStartVelocity(velocityY)
                                    setStartValue(0f)
                                    addUpdateListener { _, value, _ ->
                                        translationY = translateY + value
                                        updateDrawMatrix()
                                        informUpdated()
                                    }
                                    addEndListener { _, _, _, _ ->
                                        if (flingAboutToComplete) {
                                            gestureDetectorListener.onZoomComplete(focusX to focusY, _drawMatrix)
                                        } else {
                                            flingAboutToComplete = true
                                        }
                                    }
                                    start()
                                }
                            }
                            tracker.recycle()
                            velocityTracker = null
                        }
                    } else {
                        gestureDetectorListener.onZoomComplete(focusX to focusY, _drawMatrix)
                    }
                }
            }
        }
        return true
    }

    // update focus point, touch span and pivot point
    private fun updateTouchParameters(event: MotionEvent) {
        alwaysInTapRegion = false
        val (focusX, focusY) = event.focalPoint()
        previousFocusX = focusX
        previousFocusY = focusY
        previousTouchSpan = event.touchSpan(focusX, focusY)
        updatePivotPoint(focusX, focusY)
        updateDrawMatrix()
        event.savePointers()
        informUpdated()
    }

    private fun informUpdated() {
        gestureDetectorListener.onZoomUpdate(oldDrawMatrix, _drawMatrix)
    }

    // draw matrix is used to transform child view when drawing on the canvas
    private fun updateDrawMatrix() {
        oldDrawMatrix.set(_drawMatrix)
        _drawMatrix.reset()
        _drawMatrix.preScale(_scaling, _scaling, pivotX, pivotY)
        _drawMatrix.preRotate(_rotation, pivotX, pivotY)
        _drawMatrix.postTranslate(translationX, translationY)
        drawMatrixChanged = true
    }

    private fun updateDrawMatrix(matrix: Matrix) {
        oldDrawMatrix.set(_drawMatrix)
        _drawMatrix.set(matrix)
        drawMatrixChanged = true
    }

    // this updates the pivot point and translation error caused by changing the pivot point
    private fun updatePivotPoint(focusX: Float, focusY: Float) {
        val pivotPoint = floatArrayOf(focusX, focusY)
        _drawMatrix.invert(_touchMatrix)
        _touchMatrix.mapPoints(pivotPoint)
        pivotX = pivotPoint[0]
        pivotY = pivotPoint[1]
        // correct pivot error
        translationX = focusX - pivotX
        translationY = focusY - pivotY
    }

    private fun MotionEvent.focalPoint(): Pair<Float, Float> {
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
        return previousTouchSpan
    }

    private fun scaling(currentTouchSpan: Float): Float {
        return currentTouchSpan / previousTouchSpan
    }

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
            return rotation * 180f / PI.toFloat()
        }
        return 0f
    }

    private fun translation(
        currentFocusX: Float,
        currentFocusY: Float
    ): Pair<Float, Float> {
        return (currentFocusX - previousFocusX) to (currentFocusY - previousFocusY)
    }

    private fun MotionEvent.savePointers() {
        pointerMap.clear()
        val upIndex = if (actionMasked == MotionEvent.ACTION_POINTER_UP) actionIndex else -1
        for (pointerIndex in 0 until pointerCount) {
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

}