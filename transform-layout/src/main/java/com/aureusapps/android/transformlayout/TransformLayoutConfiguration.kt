package com.aureusapps.android.transformlayout

import android.view.ViewConfiguration

object TransformLayoutConfiguration {

    private var touchSlop = ViewConfiguration.getTouchSlop()

    fun setTouchSlop(touchSlop: Int) {
        this.touchSlop = touchSlop
    }

    fun getTouchSlop(): Int {
        return touchSlop
    }

}