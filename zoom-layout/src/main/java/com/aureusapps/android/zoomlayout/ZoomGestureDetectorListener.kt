package com.aureusapps.android.zoomlayout

import android.graphics.Matrix

interface ZoomGestureDetectorListener {
    fun onZoomStart(downPoint: Position, drawMatrix: Matrix)
    fun onZoomUpdate(oldDrawMatrix: Matrix, newDrawMatrix: Matrix)
    fun onZoomComplete(upPoint: Position, drawMatrix: Matrix)
    fun onSingleTap(tapPoint: Position)
}