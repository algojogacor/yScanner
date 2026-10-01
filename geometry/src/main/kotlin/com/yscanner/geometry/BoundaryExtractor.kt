package com.yscanner.geometry

import com.yscanner.domain.model.PointF

/**
 * Extracts ordered outer contours from a [BinaryMask].
 *
 * Pipeline per call:
 * 1. **Connected-component labelling** with **8-connectivity** (diagonal neighbours count as
 *    connected), via breadth-first flood fill. 8-connectivity matches the Moore neighbourhood used
 *    by the tracer, so a single tracer pass always covers a labelled component.
 * 2. **Moore-neighbour contour tracing** of each component's outer border, starting from the
 *    topmost-leftmost pixel of that component (guaranteed to be a border pixel whose western
 *    neighbour is background).
 * 3. Components with fewer than [minComponentArea] pixels are discarded as noise.
 * 4. Results are sorted by polygon area, largest first.
 *
 * Degenerate inputs never throw: an empty mask, a mask touching all four edges, or a single-pixel
 * mask all return a valid (possibly empty) list. The tracer is guarded by a step budget of
 * `4 * width * height + 8` so malformed inputs cannot loop forever.
 *
 * The traced contours follow pixel centres, so a straight edge appears as a staircase of one point
 * per pixel; [PolygonApproximator] reduces these to a small vertex set.
 */
class BoundaryExtractor(private val minComponentArea: Int = 64) {

    fun extract(mask: BinaryMask): List<Boundary> {
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return emptyList()

        val labels = IntArray(width * height)
        val seeds = ArrayList<Int>()
        val sizes = ArrayList<Int>()
        val queue = ArrayDeque<Int>()
        var nextLabel = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!mask.get(x, y) || labels[index] != 0) continue

                nextLabel++
                val label = nextLabel
                labels[index] = label
                queue.addLast(index)
                var count = 0
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    count++
                    val cx = current % width
                    val cy = current / width
                    for (k in 0 until 8) {
                        val nx = cx + DX[k]
                        val ny = cy + DY[k]
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                        val neighbour = ny * width + nx
                        if (mask.get(nx, ny) && labels[neighbour] == 0) {
                            labels[neighbour] = label
                            queue.addLast(neighbour)
                        }
                    }
                }
                seeds.add(index)
                sizes.add(count)
            }
        }

        val boundaries = ArrayList<Boundary>(seeds.size)
        for (i in seeds.indices) {
            if (sizes[i] < minComponentArea) continue
            val contour = traceContour(mask, labels, i + 1, seeds[i])
            if (contour.isNotEmpty()) boundaries.add(Boundary(contour))
        }
        return boundaries.sortedByDescending { it.area() }
    }

    /**
     * Moore-neighbour tracing. Neighbourhood offsets [DX]/[DY] are ordered clockwise in image
     * coordinates starting at "north" (0, -1).
     */
    private fun traceContour(mask: BinaryMask, labels: IntArray, label: Int, startIndex: Int): List<PointF> {
        val width = mask.width
        val height = mask.height
        val startX = startIndex % width
        val startY = startIndex / width

        val contour = ArrayList<PointF>()
        contour.add(PointF(startX.toFloat(), startY.toFloat()))

        var currentX = startX
        var currentY = startY
        // The topmost-leftmost pixel of a component always has a background/out-of-bounds west
        // neighbour, so it is a valid starting backtrack position.
        var backX = startX - 1
        var backY = startY

        val maxSteps = 4 * width * height + 8
        var steps = 0
        while (steps < maxSteps) {
            val directionToBack = directionIndex(backX - currentX, backY - currentY)
            val searchStart = if (directionToBack >= 0) directionToBack else 6

            var found = false
            for (k in 1..8) {
                val dir = (searchStart + k) % 8
                val nx = currentX + DX[dir]
                val ny = currentY + DY[dir]
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                if (labels[ny * width + nx] != label) continue

                val previousDir = (dir + 7) % 8
                backX = currentX + DX[previousDir]
                backY = currentY + DY[previousDir]
                currentX = nx
                currentY = ny
                found = true
                break
            }
            if (!found) break

            if (currentX == startX && currentY == startY) break
            contour.add(PointF(currentX.toFloat(), currentY.toFloat()))
            steps++
        }
        return contour
    }

    private fun directionIndex(dx: Int, dy: Int): Int {
        for (i in 0 until 8) if (DX[i] == dx && DY[i] == dy) return i
        return -1
    }

    private companion object {
        /** Clockwise neighbour offsets in image coordinates, starting at north. */
        val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    }
}
