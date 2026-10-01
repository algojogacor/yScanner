package com.yscanner.detection.extract

import com.yscanner.detection.DocumentCandidate
import com.yscanner.detection.postprocess.ProbabilityMap
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import com.yscanner.geometry.BinaryMask
import com.yscanner.geometry.BoundaryExtractor
import com.yscanner.geometry.DefaultQuadrilateralFitter
import com.yscanner.geometry.QuadrilateralFitter
import com.yscanner.geometry.pointInPolygon

/**
 * Turns a soft foreground [ProbabilityMap] into ranked [DocumentCandidate]s.
 *
 * This is the seam between the detection and geometry modules, and it is
 * deliberately model-agnostic: it consumes the neutral probability map produced
 * by the postprocessor, so a mask-emitting model and an edge-emitting model take
 * exactly the same path. It is pure Kotlin — no Android, no OpenCV — and
 * therefore fully JVM-testable.
 */
interface CandidateExtractor {
    /**
     * @param mask probability map in **model space** (e.g. 256x256).
     * @param frameWidth width of the frame the coordinates should be expressed in.
     * @param frameHeight height of that frame.
     * @return candidates ranked by area, largest first, with quads in frame space.
     */
    fun extract(mask: ProbabilityMap, frameWidth: Int, frameHeight: Int): List<DocumentCandidate>
}

/**
 * Default extraction pipeline:
 *
 * ```
 * ProbabilityMap
 *   → threshold → BinaryMask
 *   → connected components + contour tracing   (BoundaryExtractor)
 *   → enclosing quadrilateral                  (QuadrilateralFitter)
 *   → scale model space → frame space
 *   → rank by area, cap the count
 * ```
 *
 * Two details are worth calling out:
 *
 * 1. **Frame-relative size filtering lives here, not in the fitter.**
 *    `QuadrilateralFitter.fit` only ever sees a boundary, never the frame
 *    dimensions, so it cannot know whether a shape is too small to plausibly be
 *    a document. [minAreaFraction] applies that judgement using the frame area.
 *
 * 2. **Coordinates are scaled from model space to frame space.** A segmentation
 *    model runs at a fraction of camera resolution; a quad expressed in 256x256
 *    model pixels is meaningless to the camera overlay until it is scaled. The
 *    caller passes the target frame size explicitly rather than relying on an
 *    implicit ratio, because the analysis and capture streams differ in size
 *    (see `AGENTS.md §21`).
 */
class DefaultCandidateExtractor(
    /** Probability at or above which a pixel counts as document. */
    private val threshold: Float = 0.5f,
    /** Minimum component area as a fraction of the model frame area. */
    private val minAreaFraction: Float = 0.01f,
    /** Upper bound on returned candidates; the UI never needs more than a few. */
    private val maxCandidates: Int = 5,
    private val fitter: QuadrilateralFitter = DefaultQuadrilateralFitter()
) : CandidateExtractor {

    init {
        require(threshold in 0f..1f) { "threshold must be within [0,1] but was $threshold" }
        require(minAreaFraction in 0f..1f) { "minAreaFraction must be within [0,1]" }
        require(maxCandidates > 0) { "maxCandidates must be positive" }
    }

    override fun extract(
        mask: ProbabilityMap,
        frameWidth: Int,
        frameHeight: Int
    ): List<DocumentCandidate> {
        if (mask.width <= 0 || mask.height <= 0) return emptyList()
        if (frameWidth <= 0 || frameHeight <= 0) return emptyList()

        val binary = BinaryMask.fromFloatArray(mask.width, mask.height, mask.values, threshold)
        if (binary.foregroundCount() == 0) return emptyList()

        val minComponentArea = (mask.width * mask.height * minAreaFraction).toInt().coerceAtLeast(1)
        val boundaries = BoundaryExtractor(minComponentArea).extract(binary)
        if (boundaries.isEmpty()) return emptyList()

        val scaleX = frameWidth.toFloat() / mask.width.toFloat()
        val scaleY = frameHeight.toFloat() / mask.height.toFloat()

        val candidates = ArrayList<DocumentCandidate>(boundaries.size)
        for ((index, boundary) in boundaries.withIndex()) {
            val modelQuad = fitter.fit(boundary) ?: continue
            val frameQuad = scaleQuad(modelQuad, scaleX, scaleY)
            val confidence = meanProbabilityInside(mask, boundary.points)
            candidates += DocumentCandidate(
                id = "candidate-$index",
                quad = frameQuad,
                confidence = confidence,
                area = frameQuad.area()
            )
        }

        return candidates
            .sortedByDescending { it.area }
            .take(maxCandidates)
    }

    /** Scales a quad's corners from model space into frame space. */
    private fun scaleQuad(quad: Quad, scaleX: Float, scaleY: Float): Quad = Quad(
        topLeft = scaleCorner(quad.topLeft, scaleX, scaleY),
        topRight = scaleCorner(quad.topRight, scaleX, scaleY),
        bottomRight = scaleCorner(quad.bottomRight, scaleX, scaleY),
        bottomLeft = scaleCorner(quad.bottomLeft, scaleX, scaleY)
    )

    private fun scaleCorner(corner: Corner, scaleX: Float, scaleY: Float): Corner =
        Corner(corner.x * scaleX, corner.y * scaleY, corner.confidence)

    /**
     * Mean probability over the pixels of [polygon].
     *
     * Used as the candidate's confidence: a boundary that encloses mostly
     * high-probability pixels is a more trustworthy detection than one that
     * merely brushes a few. Falls back to 0 when the polygon contains no pixels.
     */
    private fun meanProbabilityInside(mask: ProbabilityMap, polygon: List<PointF>): Float {
        if (polygon.size < 3) return 0f
        val box = boundingBoxOf(polygon)
        val minX = box[0].toInt().coerceAtLeast(0)
        val maxX = box[2].toInt().coerceAtMost(mask.width - 1)
        val minY = box[1].toInt().coerceAtLeast(0)
        val maxY = box[3].toInt().coerceAtMost(mask.height - 1)
        if (minX > maxX || minY > maxY) return 0f

        var sum = 0.0
        var count = 0
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (pointInPolygon(PointF(x + 0.5f, y + 0.5f), polygon)) {
                    sum += mask.at(x, y)
                    count++
                }
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    private fun boundingBoxOf(polygon: List<PointF>): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in polygon) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}
