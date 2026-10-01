package com.yscanner.geometry

import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import kotlin.math.hypot

/**
 * Conservative first-pass corner refinement.
 *
 * Each corner is nudged toward the nearest foreground pixel of [mask] within a square window of
 * radius [searchRadius]. This is deliberately minimal — it snaps a coarse quad onto the mask edge
 * without attempting sub-pixel edge fitting. M08 extends this to full-resolution ROI refinement.
 *
 * Guarantees:
 * - If the mask is empty (or has non-positive dimensions) the input quad is returned unchanged.
 * - No corner is moved by more than [searchRadius] pixels; larger displacements are clamped.
 * - If the refined corners would form a non-convex quad, the original [quad] is returned unchanged.
 */
fun refineCorners(quad: Quad, mask: BinaryMask, searchRadius: Int = 12): Quad {
    if (searchRadius <= 0) return quad
    if (mask.width <= 0 || mask.height <= 0) return quad
    if (mask.foregroundCount() == 0) return quad

    val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
    val refined = corners.map { refineCorner(it.toPointF(), mask, searchRadius) }

    val candidate = Quad(
        topLeft = Corner(refined[0].x, refined[0].y, quad.topLeft.confidence),
        topRight = Corner(refined[1].x, refined[1].y, quad.topRight.confidence),
        bottomRight = Corner(refined[2].x, refined[2].y, quad.bottomRight.confidence),
        bottomLeft = Corner(refined[3].x, refined[3].y, quad.bottomLeft.confidence)
    )
    return if (candidate.isConvex()) candidate else quad
}

private fun refineCorner(corner: PointF, mask: BinaryMask, radius: Int): PointF {
    val minX = (corner.x - radius).toInt()
    val maxX = (corner.x + radius).toInt()
    val minY = (corner.y - radius).toInt()
    val maxY = (corner.y + radius).toInt()

    var bestDistance = Float.MAX_VALUE
    var bestX = Int.MIN_VALUE
    var bestY = Int.MIN_VALUE
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            if (!mask.get(x, y)) continue
            val distance = hypot(x + 0.5f - corner.x, y + 0.5f - corner.y)
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = x
                bestY = y
            }
        }
    }
    if (bestX == Int.MIN_VALUE) return corner

    var targetX = bestX + 0.5f
    var targetY = bestY + 0.5f
    val dx = targetX - corner.x
    val dy = targetY - corner.y
    val distance = hypot(dx, dy)
    if (distance > radius) {
        val scale = radius / distance
        targetX = corner.x + dx * scale
        targetY = corner.y + dy * scale
    }
    return PointF(targetX, targetY)
}
