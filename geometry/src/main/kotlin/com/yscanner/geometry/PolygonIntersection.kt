package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import kotlin.math.abs

/*
 * Convex-polygon intersection (Sutherland–Hodgman) and the overlap metrics built on it.
 *
 * This exists because target selection has to answer "is this the same document as last
 * frame?" without a model. Two documents that overlap heavily frame-to-frame are the same
 * document; two that barely touch are not. Intersection-over-union is the standard way to
 * express that, and it is cheap enough to run per candidate pair on every frame.
 *
 * ## Winding
 *
 * Callers are inconsistent about vertex order: com.yscanner.domain.model.Quad is defined
 * TL -> TR -> BR -> BL (positive signedPolygonArea because image y grows downwards), but a
 * polygon that has been reversed — or a contour handed in by another producer — has the
 * opposite sign. Clipping requires a *consistent* inside test, so both inputs are normalised
 * to positive (screen-clockwise) winding up front. Skipping this step silently returns an
 * empty intersection for two quads that clearly overlap, which is exactly the class of bug
 * the tests are written to catch.
 */

/** Tolerance for "on the edge" and "degenerate area" decisions, in pixels / square pixels. */
private const val EPSILON = 1e-6f

/**
 * Clips convex [subject] against convex [clip] with the Sutherland–Hodgman algorithm.
 *
 * @return the intersection polygon's vertices, in no particular canonical order, or an empty
 *   list when the two polygons do not overlap or either input is degenerate. Near-duplicate
 *   vertices are permitted in the output; [signedPolygonArea] is unaffected by them, so the
 *   area and IoU helpers do not care.
 */
fun convexPolygonIntersection(subject: List<PointF>, clip: List<PointF>): List<PointF> {
    if (subject.size < 3 || clip.size < 3) return emptyList()

    val subjectArea = signedPolygonArea(subject)
    val clipArea = signedPolygonArea(clip)
    // A near-zero area means the vertices are collinear (or duplicated); there is no
    // well-defined interior to clip, and the shoelace-derived inside test would be arbitrary.
    if (abs(subjectArea) < EPSILON || abs(clipArea) < EPSILON) return emptyList()

    // Normalise so "inside" always means "cross product >= 0" for both polygons.
    val subjectPoly = if (subjectArea < 0f) subject.reversed() else subject
    val clipPoly = if (clipArea < 0f) clip.reversed() else clip

    var output: List<PointF> = subjectPoly
    val clipSize = clipPoly.size
    for (i in 0 until clipSize) {
        if (output.isEmpty()) return emptyList()
        val edgeStart = clipPoly[i]
        val edgeEnd = clipPoly[(i + 1) % clipSize]
        output = clipAgainstEdge(output, edgeStart, edgeEnd)
    }
    return output
}

/** Area of the intersection of two convex polygons; `0f` when they do not overlap. */
fun polygonIntersectionArea(a: List<PointF>, b: List<PointF>): Float {
    val intersection = convexPolygonIntersection(a, b)
    if (intersection.size < 3) return 0f
    return abs(signedPolygonArea(intersection))
}

/**
 * Intersection-over-union of two convex polygons, in `[0,1]`.
 *
 * Returns `0f` when either polygon is degenerate, and — importantly — exactly `0f` (not a
 * tiny negative or NaN) when the two are disjoint, because callers compare the result
 * against a threshold and a stray `-1e-9` would be indistinguishable from a real overlap.
 */
fun polygonIoU(a: List<PointF>, b: List<PointF>): Float {
    val areaA = abs(signedPolygonArea(a))
    val areaB = abs(signedPolygonArea(b))
    if (areaA <= 0f || areaB <= 0f) return 0f

    val intersectionArea = polygonIntersectionArea(a, b)
    if (intersectionArea <= 0f) return 0f

    val union = areaA + areaB - intersectionArea
    if (union <= 0f) return 0f

    return (intersectionArea / union).coerceIn(0f, 1f)
}

/**
 * Clips [polygon] (assumed positively wound) against the half-plane on the inside of the
 * directed edge `[edgeStart, edgeEnd]`.
 *
 * For a positively wound polygon, a point is inside the edge when `cross(edgeStart, edgeEnd,
 * point) >= 0`. The epsilon is applied on the inside side so a vertex that lands numerically
 * on the clip edge is kept rather than dropped, which would otherwise punch a spurious notch
 * out of the intersection.
 */
private fun clipAgainstEdge(
    polygon: List<PointF>,
    edgeStart: PointF,
    edgeEnd: PointF
): List<PointF> {
    val n = polygon.size
    val result = ArrayList<PointF>(n + 1)
    for (i in 0 until n) {
        val current = polygon[i]
        val previous = polygon[(i + n - 1) % n]
        val currentInside = cross(edgeStart, edgeEnd, current) >= -EPSILON
        val previousInside = cross(edgeStart, edgeEnd, previous) >= -EPSILON

        if (currentInside) {
            if (!previousInside) {
                result.add(lineIntersection(previous, current, edgeStart, edgeEnd))
            }
            result.add(current)
        } else if (previousInside) {
            result.add(lineIntersection(previous, current, edgeStart, edgeEnd))
        }
    }
    return result
}

/**
 * Intersection of the segment `[p1, p2]` with the infinite line through `[edgeStart, edgeEnd]`.
 *
 * `cross(edgeStart, edgeEnd, ·)` is affine in its third argument, so the crossing parameter is
 * `t = c1 / (c1 - c2)` with `ci` the cross product at `pi`. The caller only reaches this with
 * `c1` and `c2` of opposite sign, so `t` lies in `(0,1)`; the guard only protects against the
 * degenerate case where both are within epsilon of the edge and the division would be 0/0.
 */
private fun lineIntersection(
    p1: PointF,
    p2: PointF,
    edgeStart: PointF,
    edgeEnd: PointF
): PointF {
    val c1 = cross(edgeStart, edgeEnd, p1)
    val c2 = cross(edgeStart, edgeEnd, p2)
    val denominator = c1 - c2
    if (abs(denominator) < EPSILON) return p2
    val t = c1 / denominator
    return PointF(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
}
