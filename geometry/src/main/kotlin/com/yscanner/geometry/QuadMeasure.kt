package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import kotlin.math.abs

/**
 * Area-weighted centroid of a [Quad].
 *
 * Lives in `:geometry` rather than `:domain` because it reuses the polygon maths here;
 * `:domain` is a leaf module and must not depend on `:geometry`. The formula matches
 * [Boundary.centroid] so a quad and the contour it came from report the same centre.
 *
 * Falls back to the arithmetic mean of the four corners when the quad is degenerate
 * (near-zero area), which keeps the result finite for a collapsed detection instead of
 * dividing by ~0.
 */
fun Quad.centroid(): PointF {
    val points = listOf(
        topLeft.toPointF(),
        topRight.toPointF(),
        bottomRight.toPointF(),
        bottomLeft.toPointF()
    )
    val n = points.size
    var twiceArea = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in 0 until n) {
        val p = points[i]
        val q = points[(i + 1) % n]
        val cross = p.x.toDouble() * q.y - q.x.toDouble() * p.y
        twiceArea += cross
        cx += (p.x + q.x).toDouble() * cross
        cy += (p.y + q.y).toDouble() * cross
    }
    if (abs(twiceArea) < 1e-9) {
        var sx = 0.0
        var sy = 0.0
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        return PointF((sx / n).toFloat(), (sy / n).toFloat())
    }
    return PointF((cx / (3.0 * twiceArea)).toFloat(), (cy / (3.0 * twiceArea)).toFloat())
}
