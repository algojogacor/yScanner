package com.yscanner.detection.postprocess

import com.yscanner.detection.model.OutputKind
import com.yscanner.detection.model.SegmentationOutput
import com.yscanner.domain.model.PointF
import kotlin.math.exp

/**
 * A single-plane, row-major probability map with values in `[0,1]`.
 *
 * This is the detection module's neutral hand-off format. It is intentionally
 * *not* the geometry module's `BinaryMask`: a probability map keeps the full
 * soft information so the downstream stage can choose its own threshold, and it
 * keeps `:detection` free to normalise whichever raw output kind the model
 * produced (mask, edge map, or corner heatmaps) into one shape.
 */
class ProbabilityMap(
    val width: Int,
    val height: Int,
    val values: FloatArray
) {
    init {
        require(width > 0 && height > 0) { "probability map dimensions must be positive" }
        require(values.size == width * height) {
            "values size ${values.size} does not match ${width}x$height"
        }
    }

    /** Value at (x, y); 0 outside the map. */
    fun at(x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0f
        return values[y * width + x]
    }

    /** Peak value in the map. 0 for a degenerate map. */
    fun max(): Float = values.maxOrNull() ?: 0f

    /** Mean value across the map. */
    fun mean(): Float = if (values.isEmpty()) 0f else values.sum() / values.size

    /**
     * Fraction of pixels at or above [threshold]. Used as a cheap
     * "is there anything here at all" signal.
     */
    fun coverageAbove(threshold: Float): Float {
        if (values.isEmpty()) return 0f
        var count = 0
        for (v in values) if (v >= threshold) count++
        return count.toFloat() / values.size
    }
}

/**
 * A decoded set of four document corners.
 *
 * Produced when a model emits corner heatmaps directly (e.g. DocAligner) rather
 * than a dense mask. Kept separate from [ProbabilityMap] because the downstream
 * handling genuinely differs: corners skip boundary extraction and envelope
 * computation entirely.
 */
data class DecodedCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
    /** Mean peak heatmap value across the four corners, in `[0,1]`. */
    val confidence: Float,
    /** True when a document-presence head fired, if the model has one. */
    val documentPresent: Boolean
)

/**
 * Normalises a raw [SegmentationOutput] into the detection module's neutral
 * representations.
 *
 * Having this as an interface (rather than inlining the maths in the model
 * adapter) is what lets a mask-emitting model and a corner-emitting model share
 * one pipeline: the adapter reports its [OutputKind] in the descriptor, and the
 * postprocessor handles the rest.
 */
interface SegmentationPostprocessor {

    /**
     * Collapses the model output into a single soft foreground probability map.
     *
     * For [OutputKind.MASK] and [OutputKind.EDGE_MAP] this is essentially the
     * identity. For [OutputKind.CORNER_HEATMAP] the four corner planes are
     * reduced (max-combined) into one map so that a mask-shaped consumer still
     * gets a usable signal — the corners themselves come from [decodeCorners].
     */
    fun toProbabilityMap(output: SegmentationOutput): ProbabilityMap

    /**
     * Decodes four corner points from a corner-heatmap output.
     * Returns null when the output is not a corner heatmap, or when no corner
     * reaches [minPeak].
     */
    fun decodeCorners(output: SegmentationOutput, minPeak: Float = 0.2f): DecodedCorners?
}

/**
 * Default postprocessor. Applies a sigmoid only when the model is known to emit
 * logits; otherwise it treats values as already-normalised probabilities and
 * simply clamps them, so a well-behaved model is not double-squashed.
 */
class DefaultSegmentationPostprocessor(
    /** Set true when the model emits raw logits rather than probabilities. */
    private val applySigmoid: Boolean = false
) : SegmentationPostprocessor {

    override fun toProbabilityMap(output: SegmentationOutput): ProbabilityMap {
        val w = output.outputWidth
        val h = output.outputHeight

        val values = when (output.descriptor.outputKind) {
            OutputKind.MASK, OutputKind.EDGE_MAP -> normalise(output.plane(0))
            OutputKind.CORNER_HEATMAP -> {
                // Reduce the 4 corner planes to a single "document-ness" signal.
                val planeSize = w * h
                val combined = FloatArray(planeSize)
                for (plane in 0 until 4) {
                    val offset = plane * planeSize
                    for (i in 0 until planeSize) {
                        val v = normaliseValue(output.confidence[offset + i])
                        if (v > combined[i]) combined[i] = v
                    }
                }
                combined
            }
        }
        return ProbabilityMap(w, h, values)
    }

    override fun decodeCorners(output: SegmentationOutput, minPeak: Float): DecodedCorners? {
        if (output.descriptor.outputKind != OutputKind.CORNER_HEATMAP) return null
        val w = output.outputWidth
        val h = output.outputHeight
        val planeSize = w * h

        val corners = ArrayList<PointF>(4)
        var confidenceSum = 0f
        for (plane in 0 until 4) {
            val offset = plane * planeSize
            var bestIndex = -1
            var bestValue = Float.NEGATIVE_INFINITY
            for (i in 0 until planeSize) {
                val v = normaliseValue(output.confidence[offset + i])
                if (v > bestValue) {
                    bestValue = v
                    bestIndex = i
                }
            }
            if (bestIndex < 0 || bestValue < minPeak) return null
            corners += PointF((bestIndex % w).toFloat(), (bestIndex / w).toFloat())
            confidenceSum += bestValue
        }

        return DecodedCorners(
            topLeft = corners[0],
            topRight = corners[1],
            bottomRight = corners[2],
            bottomLeft = corners[3],
            confidence = confidenceSum / 4f,
            documentPresent = output.documentPresent
        )
    }

    private fun normalise(values: FloatArray): FloatArray {
        val out = FloatArray(values.size)
        for (i in values.indices) out[i] = normaliseValue(values[i])
        return out
    }

    private fun normaliseValue(raw: Float): Float =
        if (applySigmoid) 1f / (1f + exp(-raw)) else raw.coerceIn(0f, 1f)
}
