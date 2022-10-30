package com.aureusapps.android.transformlayout

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.core.math.MathUtils.clamp
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import com.aureusapps.android.extensions.rotation
import com.aureusapps.android.extensions.scaling
import com.aureusapps.android.extensions.translation
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
        private const val SINGLE_TAP_TIMEOUT = 400
    }

    var isZoomEnabled = false
    var isScaleEnabled = true
    var isRotationEnabled = true
    var isTranslationEnabled = true
    var isFlingEnabled = true

    val drawMatrix: Matrix
        get() {
            tempDrawMatrix.set(_drawMatrix)
            return tempDrawMatrix
        }
    val touchMatrix: Matrix
        get() {
            if (drawMatrixChanged) {
                _drawMatrix.invert(_touchMatrix)
                drawMatrixChanged = false
            }
            return _touchMatrix
        }

    private val _touchMatrix = Matrix()
    private val _drawMatrix = Matrix()
    private val tempDrawMatrix = Matrix()
    private val pointerMap: HashMap<Int, Position> = HashMap() // touch event pointers

    private var translationX = 0f
    private var translationY = 0f
    private var scaling = 1f
    private var pivotX = 0f
    private var pivotY = 0f
    private var rotation = 0f

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

    private val previousDrawMatrix: Matrix = Matrix()
    private var drawMatrixChanged: Boolean = true

    init {
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop
        touchSlopSquare = touchSlop * touchSlop
    }

    /**
     * Update scaling, rotation and translation values.
     *
     * @param scaling Scaling value
     * @param rotation Rotation value
     * @param translation Translation value
     * @param inform Whether to inform listeners about the update
     */
    fun setTransform(
        scaling: Float = _drawMatrix.scaling,
        rotation: Float = _drawMatrix.rotation,
        translation: Position = _drawMatrix.translation,
        inform: Boolean = true
    ) {
        cancelAnims()
        this.scaling = scaling
        this.rotation = rotation
        val (tx, ty) = translation
        translationX = tx
        translationY = ty
        pivotX = 0f
        pivotY = 0f
        updateDrawMatrix(inform)
    }

    /**
     * Update scaling, rotation and translation values.
     *
     * @param matrix Matrix to update values from
     * @param inform Whether to inform listeners about the update
     */
    fun setTransform(matrix: Matrix, inform: Boolean = true) {
        cancelAnims()
        scaling = matrix.scaling
        rotation = matrix.rotation
        val (tx, ty) = matrix.translation
        translationX = tx
        translationY = ty
        pivotX = 0f
        pivotY = 0f
        updateDrawMatrix(matrix, inform)
    }

    /**
     * Concatenate given matrix to the current draw matrix.
     *
     * @param matrix Matrix to concatenate
     * @param inform Whether to inform listeners about the update
     */
    fun concatTransform(matrix: Matrix, inform: Boolean = true) {
        cancelAnims()
        _drawMatrix.postConcat(matrix)
        setTransform(_drawMatrix, inform)
    }

    /**
     * Reset transformation matrix to identity matrix.
     *
     * @param inform Whether to inform listeners about the update
     */
    fun resetTransform(inform: Boolean = true) {
        cancelAnims()
        translationX = 0f
        translationY = 0f
        scaling = 1f
        pivotX = 0f
        pivotY = 0f
        rotation = 0f
        _drawMatrix.reset()
        drawMatrixChanged = true
        if (inform) {
            informUpdated()
        }
    }

    /**
     * Scale around the given pivot point or the last pivot point.
     *
     * @param size Scaling step size. Use positive value to scale up and negative value to scale down.
     * @param px Pivot x
     * @param py Pivot y
     * @param inform Whether to inform listeners about the update
     */
    fun setScaling(
        size: Float = 0.2f,
        px: Float = pivotX,
        py: Float = pivotY,
        inform: Boolean = true
    ) {
        scaling += size
        pivotX = px
        pivotY = py
        updateDrawMatrix(inform)
    }

    /**
     * Rotate around the given pivot point or the last pivot point.
     *
     * @param angle Rotation angle in degrees
     * @param px Pivot x
     * @param py Pivot y
     */
    fun setRotation(
        angle: Float = 45f,
        px: Float = pivotX,
        py: Float = pivotY,
        inform: Boolean = true
    ) {
        rotation += angle.toRadians
        pivotX = px
        pivotY = py
        updateDrawMatrix(inform)
    }

    /**
     * Translate by dx and dy amounts.
     *
     * @param dx translate change in x direction
     * @param dy translate change in y direction
     */
    fun setTranslation(dx: Float = translationX, dy: Float = translationY) {
        if (dx == translationX && dy == translationY) return
        translationX = dx
        translationY = dy
        updateDrawMatrix()
        informUpdated()
    }

    /**
     * Call this on touch event to handle gesture detection.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        // update velocity tracker
        if (isFlingEnabled) {
            if (velocityTracker == null) {
                // To track velocity, it requires to keep track on time and touch events.
                // Computing release velocity is an expensive task as it involves integration.
                // This is the reason why VelocityTracker is implemented in native code.
                // Here we are creating [VelocityTracker] object in the native space.
                velocityTracker = VelocityTracker.obtain()
            }
            // Every touch event must be tracked.
            velocityTracker?.addMovement(event)
        }
        // handle touch events
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ACTION_DOWN is called when the first pointer touches the screen.
                // At this time, touch down point is taken as the focus point.
                // Focus point is the point around which scaling and rotation is performed relative to parent view.
                // In this case, focus point is the touch down point.
                // Pivot point is the actual point on the child view where scaling and rotation is performed.
                // We keep track on the previous focus point to calculate the translation.
                downFocusX = event.x
                downFocusY = event.y
                previousFocusX = downFocusX
                previousFocusY = downFocusY
                event.savePointers()
                // cancel ongoing fling animations
                cancelAnims()
                updatePivotPoint(downFocusX, downFocusY)
                // this is required to detect tap event
                alwaysInTapRegion = true
                gestureDetectorListener.onZoomStart(downFocusX to downFocusY, _drawMatrix)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // In a multitouch screen, subsequent touch down events are called as ACTION_POINTER_DOWN.
                // At this time, the focus point is calculated by averaging the touch points.
                // Since a new pointer is added to the list of pointers, we need to update the pivot point.
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
                            scaling *= scaling(touchSpan)
                            scaling = clamp(scaling, MIN_SCALE, MAX_SCALE)
                            previousTouchSpan = touchSpan
                        }
                        if (isRotationEnabled) {
                            rotation += event.rotation(focusX, focusY)
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
        gestureDetectorListener.onZoomUpdate(previousDrawMatrix, _drawMatrix)
    }

    // draw matrix is used to transform child view when drawing on the canvas
    private fun updateDrawMatrix(inform: Boolean = true) {
        previousDrawMatrix.set(_drawMatrix)
        _drawMatrix.reset()
        _drawMatrix.preScale(scaling, scaling, pivotX, pivotY)
        _drawMatrix.preRotate(rotation, pivotX, pivotY)
        _drawMatrix.postTranslate(translationX, translationY)
        drawMatrixChanged = true
        if (inform) {
            informUpdated()
        }
    }

    private fun updateDrawMatrix(matrix: Matrix, inform: Boolean = true) {
        previousDrawMatrix.set(_drawMatrix)
        _drawMatrix.set(matrix)
        drawMatrixChanged = true
        if (inform) {
            informUpdated()
        }
    }

    private fun updatePivotPoint(focusX: Float, focusY: Float) {
        // Location of the pivot point doesn't get changed by rotation or scaling.
        // Translation is calculated from the new pivot point.
        // So we have to compensate the translation change due to the new pivot point.
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

}