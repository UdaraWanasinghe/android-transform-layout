package com.aureusapps.android.transformlayout

import android.graphics.Matrix

interface Transformable {
    /**
     * Copy the given matrix to the transformation matrix.
     *
     * @param matrix Transform matrix to copy.
     * @param notify Whether to notify listeners about the update.
     * @return true if the transform matrix was updated.
     */
    fun setTransform(matrix: Matrix, notify: Boolean = true): Boolean

    /**
     * Copy the given values to the transform matrix.
     *
     * @param values Values to copy.
     * @param notify Whether to notify listeners about the update.
     * @return true if the transform matrix was updated.
     */
    fun setTransform(values: FloatArray, notify: Boolean = true): Boolean

    /**
     * Update scaling, rotation and translation values.
     *
     * @param scaling Scaling value around the given pivot point or the previous pivot point.
     * @param rotation Rotation value around the given pivot point or the previous pivot point in degrees.
     * @param translation Translation value to set.
     * @param pivot Point to scale and rotate around.
     * @param notify Whether to notify listeners about the update.
     * @return true if the transform matrix was updated.
     */
    fun setTransform(
        scaling: Float? = null,
        rotation: Float? = null,
        translation: Pair<Float, Float>? = null,
        pivot: Pair<Float, Float>? = null,
        notify: Boolean = true
    ): Boolean

    /**
     * Concatenate the given transform matrix to the current draw matrix.
     *
     * @param matrix Transform matrix to concatenate.
     * @param notify Whether to notify listeners about the update.
     */
    fun concatTransform(matrix: Matrix, notify: Boolean = true)

    /**
     * Concatenate the given values to the current transform matrix.
     *
     * @param values Values to concatenate.
     * @param notify Whether to notify listeners about the update.
     */
    fun concatTransform(values: FloatArray, notify: Boolean = true)

    /**
     * Concat scaling, rotation and translation values to the current transform matrix.
     *
     * @param scaling Scaling value to concat around the given pivot point or the previous pivot point.
     * @param rotation Rotation value in degrees to concat around the given pivot point or the previous pivot point.
     * @param translation Translation value to concat.
     * @param pivot Point to scale and rotate around. If not given, the previous pivot point will be used.
     * @param notify Whether to notify listeners about the update.
     */
    fun concatTransform(
        scaling: Float? = null,
        rotation: Float? = null,
        translation: Pair<Float, Float>? = null,
        pivot: Pair<Float, Float>? = null,
        notify: Boolean = true
    )

    /**
     * Reset transformation matrix to an identity matrix.
     *
     * @param notify Whether to notify listeners about the update.
     * @return true if the transform matrix is set to identity, false if already an identity.
     */
    fun resetTransform(notify: Boolean = true): Boolean
}