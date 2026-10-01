package com.localscan.common.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure Kotlin 3x3 matrix math helpers for 2D coordinate spaces and projective homography.
 * A 3x3 matrix is represented as a FloatArray of size 9 in row-major order:
 * [ m00, m01, m02,
 *   m10, m11, m12,
 *   m20, m21, m22 ]
 */
object MatrixMath {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    fun translation(tx: Float, ty: Float): FloatArray = floatArrayOf(
        1f, 0f, tx,
        0f, 1f, ty,
        0f, 0f, 1f
    )

    fun scale(sx: Float, sy: Float): FloatArray = floatArrayOf(
        sx, 0f, 0f,
        0f, sy, 0f,
        0f, 0f, 1f
    )

    fun rotation(degrees: Float): FloatArray {
        val rad = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return floatArrayOf(
            c, -s, 0f,
            s,  c, 0f,
            0f, 0f, 1f
        )
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 9 && b.size == 9) { "Matrices must be 3x3 (size 9)" }
        val result = FloatArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += a[row * 3 + k] * b[k * 3 + col]
                }
                result[row * 3 + col] = sum
            }
        }
        return result
    }

    /**
     * Transforms a 2D point (x, y) through the 3x3 projective matrix.
     * Normalized by w = m20*x + m21*y + m22.
     */
    fun mapPoint(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        require(m.size == 9) { "Matrix must be 3x3" }
        var w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-7f) w = 1f
        val px = (m[0] * x + m[1] * y + m[2]) / w
        val py = (m[3] * x + m[4] * y + m[5]) / w
        return Pair(px, py)
    }

    /**
     * Inverts a 3x3 matrix. Returns null if matrix is singular (det ≈ 0).
     */
    fun invert(m: FloatArray): FloatArray? {
        require(m.size == 9) { "Matrix must be 3x3" }
        val a00 = m[0]; val a01 = m[1]; val a02 = m[2]
        val a10 = m[3]; val a11 = m[4]; val a12 = m[5]
        val a20 = m[6]; val a21 = m[7]; val a22 = m[8]

        val det = a00 * (a11 * a22 - a12 * a21) -
                  a01 * (a10 * a22 - a12 * a20) +
                  a02 * (a10 * a21 - a11 * a20)

        if (abs(det) < 1e-9f) return null

        val invDet = 1.0f / det
        return floatArrayOf(
            (a11 * a22 - a12 * a21) * invDet,
            (a02 * a21 - a01 * a22) * invDet,
            (a01 * a12 - a02 * a11) * invDet,
            (a12 * a20 - a10 * a22) * invDet,
            (a00 * a22 - a02 * a20) * invDet,
            (a02 * a10 - a00 * a12) * invDet,
            (a10 * a21 - a11 * a20) * invDet,
            (a01 * a20 - a00 * a21) * invDet,
            (a00 * a11 - a01 * a10) * invDet
        )
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
}
