package com.yscanner.geometry

import com.yscanner.domain.image.ImageRect
import com.yscanner.domain.image.ImageRegion
import com.yscanner.domain.image.ImageSource
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Tunables for [DefaultCornerRefiner].
 *
 * @param roiSizePx side length of the square ROI decoded around each corner, in source pixels.
 * @param searchBandPx half-width of the perpendicular search band, in pixels.
 * @param samplesPerEdge how many samples to take along each edge inside the ROI.
 * @param minEdgeSamples minimum supporting edge points before a line fit is trusted.
 * @param maxDisplacementPx hard bound on how far a corner may move from its input position, in pixels.
 * @param minGradientMagnitude smallest central-difference luminance gradient (in 0..1 units) that
 *   counts as a real edge. Without this gate a flat region would still yield an "argmax" offset and
 *   fabricate an edge wherever the sampling grid happened to land.
 */
data class CornerRefinerConfig(
    val roiSizePx: Int = 320,
    val searchBandPx: Int = 14,
    val samplesPerEdge: Int = 24,
    val minEdgeSamples: Int = 8,
    val maxDisplacementPx: Float = 40f,
    val minGradientMagnitude: Float = 0.05f
)

/**
 * Pure-Kotlin [CornerRefiner] that tightens each corner against the **full-resolution** capture.
 *
 * The key idea is that detection already gives an approximate quad, so the two edges meeting at
 * each corner have approximately known directions. Rather than running a general-purpose line
 * detector over the ROI, the refiner searches **along the edges it expects**: for each edge it
 * samples points along the edge direction and, at each sample, walks a short perpendicular band to
 * find the luminance edge by central difference. A total-least-squares fit through the accepted
 * edge points gives the true edge line; intersecting the two edge lines gives the refined corner.
 *
 * This is pure Kotlin with no Android or OpenCV dependency, so it runs in JVM unit tests with a
 * fake [ImageSource] and no device.
 *
 * ### Honesty
 * This code has never been exercised against a real camera capture. The tests use synthetic images
 * with hard (non-anti-aliased) edges, where the nearest-integer central difference carries a
 * sub-pixel bias of up to about one pixel. Real captures are anti-aliased and should be at least as
 * well behaved, but that is a claim about the algorithm, not a measurement.
 *
 * The failure contract of [CornerRefiner] is honoured: this never throws, never returns a non-convex
 * quad, and never moves a corner further than [CornerRefinerConfig.maxDisplacementPx].
 */
class DefaultCornerRefiner(
    private val config: CornerRefinerConfig = CornerRefinerConfig()
) : CornerRefiner {

    override fun refine(quad: Quad, source: ImageSource): Quad {
        if (source.width <= 0 || source.height <= 0) return quad
        if (quad.area() <= AREA_EPSILON) return quad

        val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        val points = corners.map { it.toPointF() }

        val refined = ArrayList<PointF>(CORNER_COUNT)
        for (i in 0 until CORNER_COUNT) {
            // Adjacent corners for topLeft are topRight and bottomLeft; the same rotation holds for
            // the other three (i+1 and i+3 around the TL -> TR -> BR -> BL ring).
            val neighbours = listOf(
                points[(i + 1) % CORNER_COUNT],
                points[(i + 3) % CORNER_COUNT]
            )
            refined.add(refineCornerSafely(points[i], neighbours, source))
        }

        val candidate = Quad(
            topLeft = Corner(refined[0].x, refined[0].y, quad.topLeft.confidence),
            topRight = Corner(refined[1].x, refined[1].y, quad.topRight.confidence),
            bottomRight = Corner(refined[2].x, refined[2].y, quad.bottomRight.confidence),
            bottomLeft = Corner(refined[3].x, refined[3].y, quad.bottomLeft.confidence)
        )
        return if (candidate.isConvex()) candidate else quad
    }

    /** A single bad corner degrades to its input value rather than failing the whole refinement. */
    private fun refineCornerSafely(corner: PointF, neighbours: List<PointF>, source: ImageSource): PointF =
        try {
            refineCorner(corner, neighbours, source) ?: corner
        } catch (_: Exception) {
            corner
        }

    /** @return the refined corner, or `null` when refinement could not be performed confidently. */
    private fun refineCorner(corner: PointF, neighbours: List<PointF>, source: ImageSource): PointF? {
        val rect = ImageRect.centredOn(corner.x, corner.y, config.roiSizePx)
            .clampTo(source.width, source.height)
            ?: return null
        val region = source.decodeRegion(rect) ?: return null

        val lines = ArrayList<FittedLine>(neighbours.size)
        for (neighbour in neighbours) {
            val edgePoints = findEdgePoints(corner, neighbour, region)
            if (edgePoints.size < config.minEdgeSamples) return null
            val line = fitLineTotalLeastSquares(edgePoints) ?: return null
            lines.add(line)
        }
        if (lines.size < 2) return null

        val refined = intersectLines(lines[0], lines[1]) ?: return null
        return clampDisplacement(corner, refined, config.maxDisplacementPx)
    }

    /**
     * Samples the edge running from [corner] toward [neighbour] and returns the accepted edge points
     * in **source-image** coordinates.
     */
    private fun findEdgePoints(corner: PointF, neighbour: PointF, region: ImageRegion): List<PointF> {
        val ex = (neighbour.x - corner.x).toDouble()
        val ey = (neighbour.y - corner.y).toDouble()
        val length = hypot(ex, ey)
        if (length < MIN_EDGE_LENGTH) return emptyList()

        val dirX = ex / length
        val dirY = ey / length
        val normalX = -dirY
        val normalY = dirX

        val halfSize = config.roiSizePx / 2.0
        // Keep every sample inside the decoded region: maxT is bounded by both the edge length and
        // the distance from the ROI centre to its border, minus the perpendicular band.
        val maxT = minOf(length, halfSize - config.searchBandPx)
        if (maxT <= START_OFFSET_PX) return emptyList()

        // The ROI is not at the image origin, so sample positions must be expressed in region-local
        // coordinates for the `get` calls; getting this wrong silently refines the wrong pixels.
        val cornerLocalX = corner.x.toDouble() - region.originX
        val cornerLocalY = corner.y.toDouble() - region.originY

        val count = config.samplesPerEdge
        if (count <= 0) return emptyList()

        val points = ArrayList<PointF>(count)
        for (i in 0 until count) {
            val fraction = if (count == 1) 0.5 else i.toDouble() / (count - 1)
            val t = START_OFFSET_PX + (maxT - START_OFFSET_PX) * fraction
            val baseLocalX = cornerLocalX + dirX * t
            val baseLocalY = cornerLocalY + dirY * t

            // A base point outside the region would read 0f for every band sample and could
            // manufacture a false edge at the decode boundary, so such samples are discarded.
            if (baseLocalX < 0.0 || baseLocalY < 0.0 ||
                baseLocalX >= region.width || baseLocalY >= region.height
            ) {
                continue
            }

            val offset = strongestNormalOffset(region, baseLocalX, baseLocalY, normalX, normalY)
                ?: continue
            points.add(
                PointF(
                    region.toSourceX((baseLocalX + normalX * offset).toFloat()),
                    region.toSourceY((baseLocalY + normalY * offset).toFloat())
                )
            )
        }
        return points
    }

    /**
     * Walks `s` from `-searchBandPx` to `+searchBandPx` along the normal at the given base point and
     * returns the `s` with the largest absolute central-difference gradient, or `null` when no
     * sample clears [CornerRefinerConfig.minGradientMagnitude].
     */
    private fun strongestNormalOffset(
        region: ImageRegion,
        baseLocalX: Double,
        baseLocalY: Double,
        normalX: Double,
        normalY: Double
    ): Double? {
        val band = config.searchBandPx
        val threshold = config.minGradientMagnitude.toDouble()
        var bestMagnitude = 0.0
        var bestOffset = 0.0
        var found = false
        for (s in -band..band) {
            val lx = baseLocalX + normalX * s
            val ly = baseLocalY + normalY * s
            val plus = region[(lx + normalX).roundToInt(), (ly + normalY).roundToInt()]
            val minus = region[(lx - normalX).roundToInt(), (ly - normalY).roundToInt()]
            val magnitude = abs((plus - minus).toDouble() / 2.0)
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                bestOffset = s.toDouble()
                found = true
            }
        }
        return if (found && bestMagnitude >= threshold) bestOffset else null
    }

    /**
     * Clamps [refined] to at most [maxDisplacement] pixels from [original], moving along the
     * displacement direction so the distance equals the bound exactly. Non-finite input keeps
     * [original].
     */
    private fun clampDisplacement(original: PointF, refined: PointF, maxDisplacement: Float): PointF {
        if (!refined.x.isFinite() || !refined.y.isFinite()) return original

        val dx = (refined.x - original.x).toDouble()
        val dy = (refined.y - original.y).toDouble()
        val distance = hypot(dx, dy)
        if (distance <= maxDisplacement.toDouble() || distance == 0.0) return refined

        val scale = maxDisplacement / distance
        return PointF(
            (original.x + dx * scale).toFloat(),
            (original.y + dy * scale).toFloat()
        )
    }

    private companion object {
        const val CORNER_COUNT = 4

        /** Skip the corner's own neighbourhood so the *other* edge does not pollute the fit. */
        const val START_OFFSET_PX = 6.0

        /** Below this edge length (px) the neighbour is treated as coincident with the corner. */
        const val MIN_EDGE_LENGTH = 1e-3

        /** A quad with area at or below this is degenerate and returned unchanged. */
        const val AREA_EPSILON = 1e-6f
    }
}
