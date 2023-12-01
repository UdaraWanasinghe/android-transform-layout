package com.aureusapps.android.transformlayout

import android.view.MotionEvent
import android.view.ViewConfiguration
import com.aureusapps.android.transformlayout.extensions.focusPoint

class TouchStateTracker(
    touchSlop: Int = ViewConfigurationCompatExtended.getTouchSlop()
) {

    var isSingleTapped = false
        private set
    var isLongPressed = false
        private set
    var touchX = 0f
        private set
    var touchY = 0f
        private set

    private var detectSingleTap = true
    private var detectLongPress = true

    private val touchSlopSquared = touchSlop * touchSlop
    private var downFocusX = 0f
    private var downFocusY = 0f

    fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resetState()
                val (focusX, focusY) = event.focusPoint()
                downFocusX = focusX
                downFocusY = focusY
                touchX = focusX
                touchY = focusY
            }

            MotionEvent.ACTION_MOVE -> {
                val (focusX, focusY) = event.focusPoint()
                touchX = focusX
                touchY = focusY
                if (detectLongPress) {
                    val dx = focusX - downFocusX
                    val dy = focusY - downFocusY
                    val ds = dx * dx + dy * dy
                    if (ds > touchSlopSquared) {
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
            }

            MotionEvent.ACTION_UP -> {
                val dt = event.eventTime - event.downTime
                touchX = event.x
                touchY = event.y
                if (detectSingleTap) {
                    if (dt < ViewConfiguration.getTapTimeout()) {
                        isSingleTapped = true
                        return
                    }
                }
                if (detectLongPress) {
                    if (dt > ViewConfiguration.getLongPressTimeout()) {
                        isLongPressed = true
                        return
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                resetState()
            }
        }
    }

    private fun resetState() {
        isSingleTapped = false
        isLongPressed = false
        detectSingleTap = true
        detectLongPress = true
    }

}