package com.yscanner.domain.model

/**
 * A document corner vertex with location and detection confidence [0.0, 1.0].
 */
data class Corner(
    val x: Float,
    val y: Float,
    val confidence: Float = 1.0f
) {
    fun toPointF(): PointF = PointF(x, y)

    companion object {
        fun fromPointF(point: PointF, confidence: Float = 1.0f): Corner =
            Corner(point.x, point.y, confidence)
    }
}
