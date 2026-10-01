package com.localscan.domain.model

import kotlin.math.hypot

/**
 * Pure Kotlin immutable 2D floating-point coordinate representation.
 * Free of android.graphics.PointF dependencies for JVM testability.
 */
data class PointF(
    val x: Float,
    val y: Float
) {
    fun distanceTo(other: PointF): Float = hypot(x - other.x, y - other.y)

    operator fun plus(other: PointF): PointF = PointF(x + other.x, y + other.y)
    operator fun minus(other: PointF): PointF = PointF(x - other.x, y - other.y)
    operator fun times(factor: Float): PointF = PointF(x * factor, y * factor)
}
