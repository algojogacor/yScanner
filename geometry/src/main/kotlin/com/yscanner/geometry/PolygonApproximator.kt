package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import kotlin.math.hypot

/**
 * Douglas–Peucker polyline simplification.
 *
 * The algorithm keeps the first and last points and recursively retains the point that is farthest
 * from the chord of each segment whenever that distance exceeds [epsilon]. Implemented iteratively
 * with an explicit stack so long contours cannot overflow the call stack.
 *
 * - Fewer than three points, or a non-positive [epsilon], returns the input unchanged.
 * - `epsilon` is expressed in pixels; larger values remove more vertices.
 */
fun approximate(points: List<PointF>, epsilon: Float): List<PointF> {
    val n = points.size
    if (n < 3 || epsilon <= 0f) return points

    val keep = BooleanArray(n)
    keep[0] = true
    keep[n - 1] = true

    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, n - 1))
    while (stack.isNotEmpty()) {
        val range = stack.removeLast()
        val first = range[0]
        val last = range[1]
        var maxDistance = 0f
        var farthest = -1
        for (i in first + 1 until last) {
            val distance = perpendicularDistance(points[i], points[first], points[last])
            if (distance > maxDistance) {
                maxDistance = distance
                farthest = i
            }
        }
        if (farthest != -1 && maxDistance > epsilon) {
            keep[farthest] = true
            stack.addLast(intArrayOf(first, farthest))
            stack.addLast(intArrayOf(farthest, last))
        }
    }

    val result = ArrayList<PointF>()
    for (i in 0 until n) if (keep[i]) result.add(points[i])
    return result
}

/**
 * Finds the largest [epsilon] (by binary search over `[0, perimeter]`) whose Douglas–Peucker result
 * has exactly [target] vertices, and returns that simplification.
 *
 * Behaviour and conventions:
 * - The input is treated as a **closed polygon**: if the first and last points differ, the first
 *   point is appended before simplification and the duplicated closing vertex is removed afterwards.
 *   This is essential — running open-polyline Douglas–Peucker on a closed contour would pin two
 *   adjacent points as endpoints and produce a degenerate quad.
 * - Vertex count decreases monotonically as `epsilon` grows, so binary search converges.
 * - If an exact count is unreachable, the closest achievable candidate with **at least** [target]
 *   vertices is returned (a slightly over-complete polygon can be reduced by the caller, whereas an
 *   under-complete one cannot). If no candidate has at least [target] vertices, the best candidate
 *   below [target] is returned; if the input already has `<= target` points it is returned as-is.
 */
fun approximateToVertexCount(points: List<PointF>, target: Int = 4): List<PointF> {
    if (target <= 0) return points
    if (points.size <= target) return points

    val closed = closeIfNeeded(points)
    val perimeter = polygonPerimeter(closed).coerceAtLeast(1e-3f)

    fun evaluate(epsilon: Float): List<PointF> {
        val simplified = approximate(closed, epsilon)
        return if (simplified.size > 1 && simplified.first() == simplified.last()) {
            simplified.subList(0, simplified.size - 1).toList()
        } else {
            simplified
        }
    }

    var bestAtLeast = evaluate(0f)
    var bestAtLeastCount = bestAtLeast.size
    var bestBelow: List<PointF>? = null
    var bestBelowCount = -1

    var low = 0f
    var high = perimeter
    repeat(48) {
        val mid = (low + high) / 2f
        val candidate = evaluate(mid)
        val count = candidate.size
        when {
            count == target -> return candidate
            count > target -> {
                if (count < bestAtLeastCount) {
                    bestAtLeastCount = count
                    bestAtLeast = candidate
                }
                low = mid
            }
            else -> {
                if (count > bestBelowCount) {
                    bestBelowCount = count
                    bestBelow = candidate
                }
                high = mid
            }
        }
    }
    return if (bestAtLeast.isNotEmpty() && bestAtLeastCount >= target) bestAtLeast else (bestBelow ?: points)
}

private fun closeIfNeeded(points: List<PointF>): List<PointF> =
    if (points.size >= 3 && points.first() != points.last()) points + points.first() else points

private fun perpendicularDistance(p: PointF, a: PointF, b: PointF): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 1e-12f) return p.distanceTo(a)
    var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared
    t = t.coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}
