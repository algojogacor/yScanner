package com.yscanner.geometry

import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Fits a single convex, enclosing quadrilateral to a boundary contour. */
interface QuadrilateralFitter {
    /**
     * @return the best enclosing [Quad] in topLeft/topRight/bottomRight/bottomLeft clockwise order,
     *   or `null` when the boundary is too small, degenerate, or cannot be enclosed by a convex quad
     *   that passes the configured quality gates.
     */
    fun fit(boundary: Boundary): Quad?
}

/**
 * Default [QuadrilateralFitter] using a two-strategy approach over the boundary's convex hull.
 *
 * **Strategy A — Douglas–Peucker.** Simplify the convex hull to exactly four vertices with
 * [approximateToVertexCount]. This recovers the genuine document corners when the document really
 * is a quadrilateral, and is therefore preferred.
 *
 * **Strategy B — minimum-area enclosing rectangle (rotating calipers).** For every hull edge,
 * project all hull points onto that edge's orthonormal basis and take the axis-aligned extremes;
 * keep the rectangle with the smallest area. This always yields a valid convex enclosing quad and is
 * the guaranteed fallback when Strategy A cannot produce four vertices (e.g. a triangular hull) or
 * when Strategy A's quad is too small to capture the shape (e.g. a circular/octagonal document).
 *
 * Selection: each candidate is scored by its **fill ratio** `quad.area() / boundary.area()`. A fill
 * ratio of exactly `1.0` means the quad is precisely as large as the boundary; larger values mean the
 * quad is loose (it encloses extra background). The candidate whose fill ratio is **closest to 1.0**
 * is chosen, i.e. the tightest enclosing quad, with ties resolved in favour of Strategy A. This is
 * the operational reading of "how tightly the quad encloses the boundary".
 *
 * Two quality gates are applied to every candidate before scoring:
 * - `quad.area() / boundary.boundingBox().area >= minAreaRatio` — rejects a quad that is negligible
 *   relative to its **own axis-aligned bounding box**. This catches the realistic false positive of
 *   a long thin diagonal edge line fitted as a document (bounding box large, quad tiny). It does
 *   **not** reject an axis-aligned bar, because such a shape *is* its bounding box and the ratio is
 *   therefore 1.0 by construction. Absolute smallness is not knowable here — `fit` receives only the
 *   boundary, never the frame dimensions — so any "is this too small to be a document?" decision
 *   must be made by a caller that knows the frame size.
 * - `quad.area() >= minFillRatio * boundary.area` — rejects quads too small to capture the boundary.
 *
 * The result is always normalised by [orderCorners] before it is returned. `null` is returned when
 * no candidate survives.
 */
class DefaultQuadrilateralFitter(
    private val minAreaRatio: Float = 0.02f,
    private val minFillRatio: Float = 0.70f
) : QuadrilateralFitter {

    override fun fit(boundary: Boundary): Quad? {
        if (boundary.points.size < 3) return null
        val boundaryArea = boundary.area()
        if (boundaryArea <= 1e-6f) return null

        val hull = convexHull(boundary.points)
        if (hull.size < 3) return null

        val candidates = ArrayList<Quad>(2)
        strategyDouglasPeucker(hull)?.let { candidates.add(it) }
        strategyMinAreaRectangle(hull)?.let { candidates.add(it) }

        val boundingBoxArea = boundary.boundingBox().area
        var best: Quad? = null
        var bestScore = Float.MAX_VALUE
        for (candidate in candidates) {
            if (!candidate.isConvex()) continue
            if (!passesGates(candidate, boundaryArea, boundingBoxArea)) continue
            val score = abs(candidate.area() / boundaryArea - 1f)
            if (score < bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun passesGates(quad: Quad, boundaryArea: Float, boundingBoxArea: Float): Boolean {
        if (boundingBoxArea > 0f && quad.area() / boundingBoxArea < minAreaRatio) return false
        if (quad.area() < minFillRatio * boundaryArea) return false
        return true
    }

    private fun strategyDouglasPeucker(hull: List<PointF>): Quad? {
        val simplified = approximateToVertexCount(hull, 4)
        if (simplified.size != 4) return null
        return orderCorners(simplified)
    }

    private fun strategyMinAreaRectangle(hull: List<PointF>): Quad? {
        val rectangle = minAreaEnclosingRectangle(hull) ?: return null
        return orderCorners(rectangle)
    }
}

/**
 * Normalises four arbitrary points into a [Quad] ordered
 * `topLeft -> topRight -> bottomRight -> bottomLeft`.
 *
 * Convention (image coordinates, y grows downwards):
 * - Points are sorted by polar angle around their centroid in ascending order, which traverses the
 *   plane **visually clockwise** on screen.
 * - The cyclic order is then rotated to start at the point minimising `x + y` (tie-broken by `y`
 *   then `x`), i.e. the visually top-left-most corner.
 *
 * This is stable for document quads that are near-upright (rotation well under 45°). Returns `null`
 * when there are not exactly four points or any two points coincide.
 */
fun orderCorners(points: List<PointF>): Quad? {
    if (points.size != 4) return null
    for (i in 0 until 4) {
        for (j in i + 1 until 4) {
            if (points[i].distanceTo(points[j]) < 1e-4f) return null
        }
    }

    val centroidX = points.sumOf { it.x.toDouble() } / 4.0
    val centroidY = points.sumOf { it.y.toDouble() } / 4.0
    val sorted = points.sortedWith(
        compareBy { atan2(it.y.toDouble() - centroidY, it.x.toDouble() - centroidX) }
    )

    val startIndex = (0 until 4).minWithOrNull(
        compareBy<Int>(
            { sorted[it].x + sorted[it].y },
            { sorted[it].y },
            { sorted[it].x }
        )
    ) ?: 0

    val ordered = List(4) { sorted[(startIndex + it) % 4] }
    return Quad(
        topLeft = Corner(ordered[0].x, ordered[0].y),
        topRight = Corner(ordered[1].x, ordered[1].y),
        bottomRight = Corner(ordered[2].x, ordered[2].y),
        bottomLeft = Corner(ordered[3].x, ordered[3].y)
    )
}

/**
 * Minimum-area enclosing rectangle of a convex hull (rotating-calipers principle, evaluated
 * directly per hull edge in O(n^2) — hulls here are small, so simplicity beats micro-optimisation).
 *
 * For each hull edge the points are projected onto the edge direction `u` and its perpendicular `v`;
 * the rectangle spans the `[minU, maxU] x [minV, maxV]` extents in that basis. The four corners are
 * returned in world coordinates. Returns `null` for a degenerate hull (fewer than three points or
 * zero-length edges).
 */
fun minAreaEnclosingRectangle(hull: List<PointF>): List<PointF>? {
    if (hull.size < 3) return null
    var bestArea = Float.MAX_VALUE
    var bestCorners: List<PointF>? = null

    val n = hull.size
    for (i in 0 until n) {
        val p1 = hull[i]
        val p2 = hull[(i + 1) % n]
        val edgeX = p2.x - p1.x
        val edgeY = p2.y - p1.y
        val length = hypot(edgeX, edgeY)
        if (length < 1e-6f) continue

        val ux = edgeX / length
        val uy = edgeY / length
        val vx = -uy
        val vy = ux

        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (p in hull) {
            val dx = p.x - p1.x
            val dy = p.y - p1.y
            val du = dx * ux + dy * uy
            val dv = dx * vx + dy * vy
            if (du < minU) minU = du
            if (du > maxU) maxU = du
            if (dv < minV) minV = dv
            if (dv > maxV) maxV = dv
        }

        val area = (maxU - minU) * (maxV - minV)
        if (area < bestArea) {
            bestArea = area
            bestCorners = listOf(
                PointF(p1.x + ux * minU + vx * minV, p1.y + uy * minU + vy * minV),
                PointF(p1.x + ux * maxU + vx * minV, p1.y + uy * maxU + vy * minV),
                PointF(p1.x + ux * maxU + vx * maxV, p1.y + uy * maxU + vy * maxV),
                PointF(p1.x + ux * minU + vx * maxV, p1.y + uy * minU + vy * maxV)
            )
        }
    }
    return bestCorners
}
