package com.yscanner.domain.model

import kotlin.math.abs

/**
 * An ordered quadrilateral defined by four corners:
 * topLeft, topRight, bottomRight, bottomLeft (clockwise order).
 */
data class Quad(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomRight: Corner,
    val bottomLeft: Corner
) {
    /**
     * Serializes coordinates into an 8-float array:
     * [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
     */
    fun toArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    private fun crossProduct(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return x1 * y2 - y1 * x2
    }

    /**
     * Determines whether the quadrilateral is strictly convex.
     * In a strictly convex polygon, all consecutive edge cross products have the identical strict sign.
     * Order of vertices: topLeft -> topRight -> bottomRight -> bottomLeft.
     */
    fun isConvex(): Boolean {
        // Directed edges between consecutive vertices: p0 -> p1 -> p2 -> p3 -> p0
        val e0x = topRight.x - topLeft.x
        val e0y = topRight.y - topLeft.y

        val e1x = bottomRight.x - topRight.x
        val e1y = bottomRight.y - topRight.y

        val e2x = bottomLeft.x - bottomRight.x
        val e2y = bottomLeft.y - bottomRight.y

        val e3x = topLeft.x - bottomLeft.x
        val e3y = topLeft.y - bottomLeft.y

        val cp0 = crossProduct(e0x, e0y, e1x, e1y)
        val cp1 = crossProduct(e1x, e1y, e2x, e2y)
        val cp2 = crossProduct(e2x, e2y, e3x, e3y)
        val cp3 = crossProduct(e3x, e3y, e0x, e0y)

        val epsilon = 1e-4f
        val allPositive = cp0 > epsilon && cp1 > epsilon && cp2 > epsilon && cp3 > epsilon
        val allNegative = cp0 < -epsilon && cp1 < -epsilon && cp2 < -epsilon && cp3 < -epsilon

        return allPositive || allNegative
    }

    /**
     * Calculates the polygon area using the Shoelace formula (Gauss's area formula):
     * Area = 0.5 * |(x0*y1 - y0*x1) + (x1*y2 - y1*x2) + (x2*y3 - y2*x3) + (x3*y0 - y3*x0)|
     */
    fun area(): Float {
        val p0 = topLeft
        val p1 = topRight
        val p2 = bottomRight
        val p3 = bottomLeft
        val sum = (p0.x * p1.y - p0.y * p1.x) +
                  (p1.x * p2.y - p1.y * p2.x) +
                  (p2.x * p3.y - p2.y * p3.x) +
                  (p3.x * p0.y - p3.y * p0.x)
        return abs(sum) * 0.5f
    }

    companion object {
        fun fromArray(array: FloatArray): Quad {
            require(array.size >= 8) { "Array must contain at least 8 floats" }
            return Quad(
                topLeft = Corner(array[0], array[1]),
                topRight = Corner(array[2], array[3]),
                bottomRight = Corner(array[4], array[5]),
                bottomLeft = Corner(array[6], array[7])
            )
        }
    }
}
