package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Minimal axis-aligned rectangle expressed in image pixel coordinates.
 *
 * [left]/[top] are the minimum coordinates, [right]/[bottom] the maximum. This deliberately does
 * not reuse `android.graphics.RectF` so the whole geometry core stays JVM-unit-testable.
 */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

/**
 * An ordered set of boundary points (a contour) in image pixel coordinates.
 *
 * Coordinate convention: x grows to the right, y grows **downwards** (image coordinates). A
 * boundary produced by [BoundaryExtractor] is a closed contour that does **not** repeat its first
 * point; [signedArea] / [area] / [perimeter] therefore close the loop implicitly via `(i + 1) % n`.
 */
data class Boundary(val points: List<PointF>) {

    /** A boundary can be treated as a closed polygon once it has at least three points. */
    val isClosed: Boolean get() = points.size >= 3

    /** Shoelace (signed) area. Positive for visually-clockwise winding in image coordinates. */
    fun signedArea(): Float = signedPolygonArea(points)

    /** Absolute polygon area in square pixels. */
    fun area(): Float = abs(signedArea())

    /** Closed polygon perimeter in pixels. */
    fun perimeter(): Float = polygonPerimeter(points)

    /**
     * Area-weighted polygon centroid. Falls back to the arithmetic mean of the vertices when the
     * polygon is degenerate (near-zero area), and to the origin for an empty boundary.
     */
    fun centroid(): PointF {
        val n = points.size
        if (n == 0) return PointF(0f, 0f)
        if (n < 3) return meanOfPoints(points)
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
        if (abs(twiceArea) < 1e-9) return meanOfPoints(points)
        return PointF((cx / (3.0 * twiceArea)).toFloat(), (cy / (3.0 * twiceArea)).toFloat())
    }

    /** Tight axis-aligned bounding box of all points. */
    fun boundingBox(): RectF {
        if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /** True when [point] lies inside or on the edge of this boundary polygon. */
    fun contains(point: PointF): Boolean = pointInPolygon(point, points)

    private fun meanOfPoints(list: List<PointF>): PointF {
        var sx = 0.0
        var sy = 0.0
        for (p in list) {
            sx += p.x
            sy += p.y
        }
        val n = list.size
        return PointF((sx / n).toFloat(), (sy / n).toFloat())
    }
}

/**
 * Signed polygon area via the shoelace (Gauss) formula.
 *
 * The loop is closed implicitly (`(i + 1) % n`). Because image coordinates have y growing
 * downwards, a positive result corresponds to a **visually clockwise** winding on screen, e.g.
 * `topLeft -> topRight -> bottomRight -> bottomLeft`. Returns 0 for fewer than three points.
 */
fun signedPolygonArea(points: List<PointF>): Float {
    val n = points.size
    if (n < 3) return 0f
    var sum = 0.0
    for (i in 0 until n) {
        val p = points[i]
        val q = points[(i + 1) % n]
        sum += p.x.toDouble() * q.y - q.x.toDouble() * p.y
    }
    return (sum * 0.5).toFloat()
}

/** Closed polygon perimeter in pixels (implicitly closes the loop). */
fun polygonPerimeter(points: List<PointF>): Float {
    val n = points.size
    if (n < 2) return 0f
    var sum = 0.0
    for (i in 0 until n) {
        val p = points[i]
        val q = points[(i + 1) % n]
        sum += hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble())
    }
    return sum.toFloat()
}

/** 2D cross product of `(a - o)` and `(b - o)`. Positive => counter-clockwise (left turn). */
fun cross(o: PointF, a: PointF, b: PointF): Float =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

/** Shortest distance from [p] to the segment `[a, b]`. */
fun distanceToSegment(p: PointF, a: PointF, b: PointF): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq <= 1e-12f) return p.distanceTo(a)
    var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
    t = t.coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

/**
 * Point-in-polygon test using ray casting (even-odd rule).
 *
 * Points lying exactly on an edge (within 1e-3 px) are reported as **inside** so that callers such
 * as [BinaryMask.filledQuad] do not lose boundary pixels. Returns false for degenerate polygons
 * with fewer than three points.
 */
fun pointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
    val n = polygon.size
    if (n < 3) return false
    if (isPointOnBoundary(point, polygon)) return true
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > point.y) != (pj.y > point.y)) {
            val xIntersect = (pj.x - pi.x).toDouble() * (point.y - pi.y).toDouble() /
                (pj.y - pi.y).toDouble() + pi.x.toDouble()
            if (point.x.toDouble() < xIntersect) inside = !inside
        }
        j = i
    }
    return inside
}

private fun isPointOnBoundary(point: PointF, polygon: List<PointF>): Boolean {
    val n = polygon.size
    for (i in 0 until n) {
        if (distanceToSegment(point, polygon[i], polygon[(i + 1) % n]) <= 1e-3f) return true
    }
    return false
}

/**
 * Convex hull via Andrew's monotone chain, O(n log n).
 *
 * Conventions:
 * - Duplicate points are removed and the hull is strictly convex (collinear points on an edge are
 *   dropped, so an axis-aligned rectangle yields exactly its four corners).
 * - The result is returned **counter-clockwise under standard mathematical orientation** (positive
 *   [signedPolygonArea] / positive [cross] turns). Because image coordinates have y growing
 *   downwards, this ordering appears **visually clockwise** on screen: for an axis-aligned
 *   rectangle it is `topLeft -> topRight -> bottomRight -> bottomLeft`, matching [com.yscanner.domain.model.Quad].
 * - Fewer than three input points (or all-collinear input) returns the two extreme points, which
 *   callers must treat as degenerate.
 */
fun convexHull(points: List<PointF>): List<PointF> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size <= 2) return sorted

    val lower = ArrayList<PointF>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }

    val upper = ArrayList<PointF>()
    for (i in sorted.indices.reversed()) {
        val p = sorted[i]
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }

    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}
