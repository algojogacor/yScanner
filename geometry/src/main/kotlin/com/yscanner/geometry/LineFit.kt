package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A line in image pixel coordinates, expressed as a point plus a **unit** direction vector.
 *
 * [direction] has no meaningful sign: `d` and `-d` describe the same line, so callers that compare
 * directions must be sign-agnostic. This is deliberate — a total-least-squares fit cannot know
 * which way along the edge its samples were taken.
 */
data class FittedLine(val point: PointF, val direction: PointF)

/**
 * Total-least-squares (orthogonal) line fit, via a closed-form principal-component analysis.
 *
 * Orthogonal distance is the correct metric for this refiner because edge samples are noisy in the
 * **perpendicular** direction only (the search walks across the edge, not along it). An ordinary
 * least-squares `y = mx + b` fit would instead minimise vertical error and become ill-conditioned
 * for near-vertical edges, so it is not used.
 *
 * The fit is computed in `Double` throughout — these are pixel coordinates on a multi-megapixel
 * capture, where `Float` accumulation is visibly inaccurate over a long edge — and converted back
 * at the boundary.
 *
 * @return the fitted line, or `null` when there are fewer than two points or the points are
 *   coincident (a zero-spread cloud has no meaningful direction).
 */
fun fitLineTotalLeastSquares(points: List<PointF>): FittedLine? {
    if (points.size < 2) return null

    val n = points.size
    var sumX = 0.0
    var sumY = 0.0
    for (p in points) {
        sumX += p.x
        sumY += p.y
    }
    val meanX = sumX / n
    val meanY = sumY / n

    // 2x2 covariance of the centred samples: [[sxx, sxy], [sxy, syy]].
    var sxx = 0.0
    var sxy = 0.0
    var syy = 0.0
    for (p in points) {
        val dx = p.x - meanX
        val dy = p.y - meanY
        sxx += dx * dx
        sxy += dx * dy
        syy += dy * dy
    }

    // The total squared spread about the centroid is the trace (sxx + syy). When it is ~0 every
    // sample is the same pixel and the principal direction is undefined.
    if (sxx + syy < MIN_SPREAD) return null

    // Principal (largest-eigenvalue) axis of the covariance matrix, in closed form.
    val angle = 0.5 * atan2(2.0 * sxy, sxx - syy)
    return FittedLine(
        point = PointF(meanX.toFloat(), meanY.toFloat()),
        direction = PointF(cos(angle).toFloat(), sin(angle).toFloat())
    )
}

/**
 * Intersection of two lines, or `null` when they are (near-)parallel.
 *
 * Solves `p1 + t*d1 = p2 + s*d2`.
 *
 * The epsilon here is deliberately tiny, so this guard fires only when the directions are parallel
 * to within ~[PARALLEL_EPSILON] radians — effectively exact degeneracy, which does happen when two
 * adjacent quad edges are collinear. It is **not** a general safeguard against a badly conditioned
 * intersection: two directions a mere `1e-6` rad apart pass this test and still produce an
 * intersection roughly `1e6` times the lines' separation away. The bound that keeps such a point
 * from moving a corner is the caller's displacement clamp (see
 * [CornerRefinerConfig.maxDisplacementPx]), not this function. Do not rely on this epsilon
 * for that purpose.
 *
 * `Double` is used internally for the same reason as in [fitLineTotalLeastSquares].
 */
fun intersectLines(a: FittedLine, b: FittedLine): PointF? {
    val d1x = a.direction.x.toDouble()
    val d1y = a.direction.y.toDouble()
    val d2x = b.direction.x.toDouble()
    val d2y = b.direction.y.toDouble()

    val denominator = d1x * d2y - d1y * d2x
    if (abs(denominator) < PARALLEL_EPSILON) return null

    val dx = b.point.x.toDouble() - a.point.x.toDouble()
    val dy = b.point.y.toDouble() - a.point.y.toDouble()
    val t = (dx * d2y - dy * d2x) / denominator

    return PointF(
        (a.point.x + t * d1x).toFloat(),
        (a.point.y + t * d1y).toFloat()
    )
}

/** Below this total squared spread (in px^2) a sample cloud is treated as coincident. */
private const val MIN_SPREAD = 1e-9

/** Directions are unit vectors, so the cross product is `sin(angle)`; below this the lines are parallel. */
private const val PARALLEL_EPSILON = 1e-9
