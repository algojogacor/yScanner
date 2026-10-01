package com.yscanner.detection.preprocess

import java.nio.ByteBuffer

/**
 * One plane of a planar/interleaved image buffer.
 *
 * Mirrors the shape of `android.media.Image.Plane` without depending on it, so
 * the conversion maths below stays JVM-testable.
 */
class PlaneData(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int
) {
    init {
        require(rowStride > 0) { "rowStride must be positive" }
        require(pixelStride > 0) { "pixelStride must be positive" }
    }

    /**
     * Reads the byte at (x, y) using this plane's strides.
     * Returns 0 for out-of-range coordinates rather than throwing.
     */
    fun byteAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0) return 0
        val index = y * rowStride + x * pixelStride
        if (index < 0 || index >= buffer.limit()) return 0
        return buffer.get(index).toInt() and 0xFF
    }
}

/**
 * An Android-free representation of one camera frame in YUV_420_888 layout.
 *
 * Kept deliberately free of `android.media.Image` so the entire preprocessing
 * path (colour conversion, rotation, scaling, normalisation) can be unit tested
 * on the JVM with hand-built buffers — no device, no Robolectric.
 *
 * @param rotationDegrees clockwise rotation needed to make the frame upright.
 *   Must be one of 0, 90, 180, 270.
 */
class FrameData(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    /** Exactly three planes: Y, U, V — in that order. */
    val planes: List<PlaneData>
) {
    init {
        require(width > 0 && height > 0) { "frame dimensions must be positive" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be 0, 90, 180 or 270 but was $rotationDegrees"
        }
        require(planes.size == 3) { "YUV_420_888 requires exactly 3 planes but got ${planes.size}" }
    }

    val yPlane: PlaneData get() = planes[0]
    val uPlane: PlaneData get() = planes[1]
    val vPlane: PlaneData get() = planes[2]

    /** True when no rotation is needed. */
    val isUpright: Boolean get() = rotationDegrees == 0

    /** Dimensions after [rotationDegrees] is applied. */
    val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) height else width

    companion object {
        /**
         * Builds a tightly-packed YUV_420_888 frame from planar byte arrays.
         * Intended for tests and for adapters that already hold planar data.
         */
        fun fromPlanar(
            width: Int,
            height: Int,
            rotationDegrees: Int,
            y: ByteArray,
            u: ByteArray,
            v: ByteArray
        ): FrameData {
            require(y.size >= width * height) { "Y plane too small" }
            val chromaWidth = (width + 1) / 2
            val chromaHeight = (height + 1) / 2
            require(u.size >= chromaWidth * chromaHeight) { "U plane too small" }
            require(v.size >= chromaWidth * chromaHeight) { "V plane too small" }
            return FrameData(
                width = width,
                height = height,
                rotationDegrees = rotationDegrees,
                planes = listOf(
                    PlaneData(ByteBuffer.wrap(y), rowStride = width, pixelStride = 1),
                    PlaneData(ByteBuffer.wrap(u), rowStride = chromaWidth, pixelStride = 1),
                    PlaneData(ByteBuffer.wrap(v), rowStride = chromaWidth, pixelStride = 1)
                )
            )
        }
    }
}
