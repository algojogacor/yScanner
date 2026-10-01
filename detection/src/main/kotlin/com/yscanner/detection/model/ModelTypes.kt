package com.yscanner.detection.model

/**
 * Numeric element type of a model's input tensor.
 */
enum class ModelDType {
    FLOAT32,
    UINT8
}

/**
 * The *semantic kind* of what a segmentation model emits.
 *
 * This exists so the detection module is not hard-wired to one model family.
 * Spike S01 found that the only purpose-built, permissively-licensed,
 * document-specific pretrained model (DocAligner) emits **corner heatmaps**
 * rather than a dense foreground mask, while classical and U-Net-family
 * approaches emit a mask, and edge detectors emit an edge map.
 *
 * Recording the output kind in the model descriptor lets [SegmentationOutput]
 * carry whichever representation the model produced, and lets the downstream
 * candidate extraction stage branch on it — instead of forcing every model
 * through a mask-shaped contract it cannot satisfy.
 *
 * See `plans/spikes/S01-candidates.md` §7 for the decision this supports.
 */
enum class OutputKind {
    /** Dense per-pixel document probability map. The mask-shaped contract. */
    MASK,

    /** Per-corner heatmaps (4 channels), e.g. DocAligner. No dense mask. */
    CORNER_HEATMAP,

    /** Per-pixel edge/boundary probability map, e.g. HED / DexiNed / TEED. */
    EDGE_MAP
}

/**
 * Input normalisation applied when converting a frame into a model tensor.
 *
 * @param scale multiplier applied to raw pixel values before bias.
 * @param mean per-channel mean subtracted after scaling, or empty for none.
 * @param std per-channel standard deviation divisor, or empty for none.
 */
data class Normalization(
    val scale: Float = 1f / 255f,
    val mean: FloatArray = floatArrayOf(0f, 0f, 0f),
    val std: FloatArray = floatArrayOf(1f, 1f, 1f)
) {
    init {
        require(mean.isEmpty() || mean.size == 3) { "mean must be empty or have 3 channels" }
        require(std.isEmpty() || std.size == 3) { "std must be empty or have 3 channels" }
        require(std.isEmpty() || std.all { it != 0f }) { "std must not contain zero" }
    }

    // FloatArray in a data class requires manual equals/hashCode to behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Normalization) return false
        return scale == other.scale &&
            mean.contentEquals(other.mean) &&
            std.contentEquals(other.std)
    }

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + mean.contentHashCode()
        result = 31 * result + std.contentHashCode()
        return result
    }

    companion object {
        /** Identity in the sense of "raw 0..255 kept as-is". */
        val NONE = Normalization(scale = 1f, mean = floatArrayOf(), std = floatArrayOf())

        /** Standard [0,1] scaling with no per-channel shift. */
        val UNIT = Normalization(scale = 1f / 255f, mean = floatArrayOf(), std = floatArrayOf())
    }
}

/**
 * Static, inspectable description of a model. Everything the pipeline needs to
 * know about a model *without* holding its weights.
 *
 * The detection pipeline reads this to size its input buffers and to decide how
 * to interpret the output — so swapping the model implementation never requires
 * changing the pipeline.
 */
data class ModelDescriptor(
    /** Stable identifier, e.g. "docaligner-lc100" or "unet-mbv3-256". */
    val id: String,
    /** Model version or weights revision, recorded for reproducibility. */
    val version: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int = 3,
    val dtype: ModelDType = ModelDType.FLOAT32,
    val normalization: Normalization = Normalization.UNIT,
    val outputKind: OutputKind = OutputKind.MASK,
    /** Expected output height. Equal to [inputHeight] unless the model downsamples. */
    val outputHeight: Int = inputHeight,
    /** Expected output width. Equal to [inputWidth] unless the model downsamples. */
    val outputWidth: Int = inputWidth,
    /** Approximate on-disk size, for budget tracking. -1 when unknown. */
    val approximateSizeBytes: Long = -1L
) {
    init {
        require(id.isNotBlank()) { "model id must not be blank" }
        require(inputWidth > 0 && inputHeight > 0) { "input dimensions must be positive" }
        require(inputChannels > 0) { "input channels must be positive" }
        require(outputWidth > 0 && outputHeight > 0) { "output dimensions must be positive" }
    }

    /** Number of elements in one input tensor. */
    val inputElementCount: Int get() = inputWidth * inputHeight * inputChannels

    /** Number of elements in one output plane. */
    val outputElementCount: Int get() = outputWidth * outputHeight
}

/**
 * A single frame, already converted into a model-ready tensor.
 *
 * [data] is a direct or heap buffer laid out as planar or interleaved according
 * to the producing preprocessor; the model adapter is responsible for knowing
 * which. It is rewound to position 0 before every inference.
 */
class ModelInput(
    val data: java.nio.ByteBuffer,
    val descriptor: ModelDescriptor
) {
    val width: Int get() = descriptor.inputWidth
    val height: Int get() = descriptor.inputHeight
    val channels: Int get() = descriptor.inputChannels

    /** Rewinds the buffer so it can be fed to an interpreter again. */
    fun rewind(): ModelInput {
        data.rewind()
        return this
    }
}

/**
 * Raw output of a segmentation model.
 *
 * Interpretation depends on [descriptor].outputKind:
 *  - [OutputKind.MASK] / [OutputKind.EDGE_MAP]: [confidence] holds
 *    `outputWidth * outputHeight` values in `[0,1]`, row-major.
 *  - [OutputKind.CORNER_HEATMAP]: [confidence] holds
 *    `4 * outputWidth * outputHeight` values, one plane per corner in the order
 *    topLeft, topRight, bottomRight, bottomLeft.
 *
 * A single confidence array is used rather than four nullable fields so the
 * common case allocates once and the corner case stays a pure layout concern.
 */
class SegmentationOutput(
    val descriptor: ModelDescriptor,
    val confidence: FloatArray,
    /** Model's own view of whether a document is present at all. */
    val documentPresent: Boolean,
    /** Wall-clock inference duration, measured by the runtime, in milliseconds. */
    val inferenceLatencyMs: Long
) {
    val outputWidth: Int get() = descriptor.outputWidth
    val outputHeight: Int get() = descriptor.outputHeight

    /** Number of confidence planes: 1 for masks/edges, 4 for corner heatmaps. */
    val planeCount: Int
        get() = if (descriptor.outputKind == OutputKind.CORNER_HEATMAP) 4 else 1

    init {
        val expected = outputWidth * outputHeight * planeCount
        require(confidence.size == expected) {
            "confidence array size ${confidence.size} does not match expected $expected " +
                "for ${descriptor.outputKind} at ${outputWidth}x$outputHeight"
        }
    }

    /** Confidence at a single pixel of plane [plane]. Returns 0 when out of range. */
    fun at(x: Int, y: Int, plane: Int = 0): Float {
        if (x < 0 || y < 0 || x >= outputWidth || y >= outputHeight) return 0f
        if (plane < 0 || plane >= planeCount) return 0f
        return confidence[plane * outputWidth * outputHeight + y * outputWidth + x]
    }

    /**
     * Returns plane [plane] as an independent row-major array of size
     * `outputWidth * outputHeight`. Always a copy — callers may not alias the
     * model's buffer.
     */
    fun plane(plane: Int): FloatArray {
        require(plane in 0 until planeCount) { "plane $plane out of range 0..${planeCount - 1}" }
        val planeSize = outputWidth * outputHeight
        return confidence.copyOfRange(plane * planeSize, (plane + 1) * planeSize)
    }
}
