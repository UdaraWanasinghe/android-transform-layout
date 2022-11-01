package com.aureusapps.android.transformlayout

import android.graphics.Matrix

interface TransformGestureDetectorListener {

    /**
     * Called when a transform gesture has started (On ACTION_DOWN).
     *
     * @param px The x coordinate of the down point.
     * @param py The y coordinate of the down point.
     */
    fun onTransformStart(px: Float, py: Float, matrix: Matrix)

    /**
     * Called when a transform gesture has been updated (On ACTION_MOVE).
     * In a multitouch gesture, [px] and [py] are the average of x and y coordinates of the touch pointers.
     *
     * @param px The x coordinate of the pivot point.
     * @param py The y coordinate of the pivot point.
     * @param oldMatrix The matrix before the update.
     * @param newMatrix The matrix after the update.
     */
    fun onTransformUpdate(px: Float, py: Float, oldMatrix: Matrix, newMatrix: Matrix)

    /**
     * Called when a transform gesture has ended (On ACTION_UP).
     *
     * @param px The x coordinate of the up point.
     * @param py The y coordinate of the up point.
     * @param matrix The matrix after the transform.
     */
    fun onTransformComplete(px: Float, py: Float, matrix: Matrix)

    /**
     * Called when a single tap has been detected.
     *
     * @param px The x coordinate of the tap point.
     * @param py The y coordinate of the tap point.
     */
    fun onSingleTap(px: Float, py: Float)

}