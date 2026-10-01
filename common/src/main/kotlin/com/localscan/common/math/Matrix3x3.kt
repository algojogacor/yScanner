package com.localscan.common.math

import kotlin.math.abs

/**
 * Immutable value class representing a 3x3 projective transformation matrix.
 * Backed by FloatArray of length 9 in row-major order:
 * [ m00, m01, m02,
 *   m10, m11, m12,
 *   m20, m21, m22 ]
 */
data class Matrix3x3(val values: FloatArray) {
    init {
        require(values.size == 9) { "Matrix3x3 requires exactly 9 elements, got ${values.size}" }
    }

    operator fun times(other: Matrix3x3): Matrix3x3 =
        Matrix3x3(MatrixMath.multiply(this.values, other.values))

    fun mapPoint(x: Float, y: Float): Pair<Float, Float> =
        MatrixMath.mapPoint(values, x, y)

    fun invert(): Matrix3x3? {
        val inv = MatrixMath.invert(values) ?: return null
        return Matrix3x3(inv)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix3x3) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String {
        return "Matrix3x3([" +
                "${values[0]}, ${values[1]}, ${values[2]}, " +
                "${values[3]}, ${values[4]}, ${values[5]}, " +
                "${values[6]}, ${values[7]}, ${values[8]}])"
    }

    companion object {
        val IDENTITY = Matrix3x3(MatrixMath.identity())

        fun translation(tx: Float, ty: Float): Matrix3x3 =
            Matrix3x3(MatrixMath.translation(tx, ty))

        fun scale(sx: Float, sy: Float): Matrix3x3 =
            Matrix3x3(MatrixMath.scale(sx, sy))

        fun rotation(degrees: Float): Matrix3x3 =
            Matrix3x3(MatrixMath.rotation(degrees))

        /**
         * Rotation matrix for normalized [0, 1] x [0, 1] unit square coordinate space.
         * Rotates coordinates clockwise by [degrees] (normalized modulo 360).
         */
        fun rotationUnitSquare(degrees: Int): Matrix3x3 {
            val normalizedDegrees = ((degrees % 360) + 360) % 360
            return when (normalizedDegrees) {
                0 -> IDENTITY
                90 -> Matrix3x3(floatArrayOf(
                    0f, -1f, 1f,
                    1f,  0f, 0f,
                    0f,  0f, 1f
                ))
                180 -> Matrix3x3(floatArrayOf(
                    -1f,  0f, 1f,
                     0f, -1f, 1f,
                     0f,  0f, 1f
                ))
                270 -> Matrix3x3(floatArrayOf(
                     0f, 1f, 0f,
                    -1f, 0f, 1f,
                     0f, 0f, 1f
                ))
                else -> {
                    translation(0.5f, 0.5f) * rotation(normalizedDegrees.toFloat()) * translation(-0.5f, -0.5f)
                }
            }
        }

        /**
         * Computes the 3x3 projective homography mapping an arbitrary source quadrilateral
         * (x0,y0=TopLeft, x1,y1=TopRight, x2,y2=BottomRight, x3,y3=BottomLeft) in clockwise order
         * to a target rectangle with origin (0,0), width [dstWidth], and height [dstHeight].
         */
        fun homographyFromQuadToRect(
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            x3: Float, y3: Float,
            dstWidth: Float,
            dstHeight: Float
        ): Matrix3x3 {
            val hUnitToQ = homographyFromUnitSquareToQuad(x0, y0, x1, y1, x2, y2, x3, y3)
            val hQToUnit = hUnitToQ.invert() ?: IDENTITY
            val sRect = scale(dstWidth, dstHeight)
            return sRect * hQToUnit
        }

        /**
         * Computes homography mapping the canonical unit square [0,1]^2:
         * (0,0) -> (x0,y0), (1,0) -> (x1,y1), (1,1) -> (x2,y2), (0,1) -> (x3,y3).
         * Implementation of Heckbert's projective mapping algorithm.
         */
        fun homographyFromUnitSquareToQuad(
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            x3: Float, y3: Float
        ): Matrix3x3 {
            val deltaX1 = x1 - x2
            val deltaX2 = x3 - x2
            val deltaX3 = x0 - x1 + x2 - x3

            val deltaY1 = y1 - y2
            val deltaY2 = y3 - y2
            val deltaY3 = y0 - y1 + y2 - y3

            if (abs(deltaX3) < 1e-6f && abs(deltaY3) < 1e-6f) {
                // Affine mapping (parallelogram)
                return Matrix3x3(floatArrayOf(
                    x1 - x0, x3 - x0, x0,
                    y1 - y0, y3 - y0, y0,
                    0f, 0f, 1f
                ))
            }

            val denom = deltaX1 * deltaY2 - deltaY1 * deltaX2
            if (abs(denom) < 1e-9f) {
                // Degenerate quadrilateral, fallback to affine
                return Matrix3x3(floatArrayOf(
                    x1 - x0, x3 - x0, x0,
                    y1 - y0, y3 - y0, y0,
                    0f, 0f, 1f
                ))
            }

            val a31 = (deltaX3 * deltaY2 - deltaY3 * deltaX2) / denom
            val a32 = (deltaX1 * deltaY3 - deltaY1 * deltaX3) / denom

            val a11 = x1 - x0 + a31 * x1
            val a12 = x3 - x0 + a32 * x3
            val a13 = x0

            val a21 = y1 - y0 + a31 * y1
            val a22 = y3 - y0 + a32 * y3
            val a23 = y0

            return Matrix3x3(floatArrayOf(
                a11, a12, a13,
                a21, a22, a23,
                a31, a32, 1f
            ))
        }
    }
}
