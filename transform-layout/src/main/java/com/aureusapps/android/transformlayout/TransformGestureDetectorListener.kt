package com.aureusapps.android.transformlayout

import android.graphics.Matrix

interface TransformGestureDetectorListener {
    fun onZoomStart(downPoint: Position, drawMatrix: Matrix)
    fun onZoomUpdate(oldDrawMatrix: Matrix, newDrawMatrix: Matrix)
    fun onZoomComplete(upPoint: Position, drawMatrix: Matrix)
    fun onSingleTap(tapPoint: Position)
}