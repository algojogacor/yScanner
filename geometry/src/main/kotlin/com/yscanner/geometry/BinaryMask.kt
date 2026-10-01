package com.yscanner.geometry

import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad

/**
 * A dense binary foreground mask over an image of [width] x [height] pixels.
 *
 * Pixel `(x, y)` maps to `data[y * width + x]`. Any non-zero byte is foreground. All read access
 * goes through [get], which is bounds-safe (out-of-range coordinates return `false`), so tracing
 * code can probe neighbours without explicit range checks.
 *
 * The class intentionally has no Android or OpenCV dependency: masks are constructed from plain
 * arrays so the geometry core can be exercised by JVM unit tests.
 */
class BinaryMask(
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    init {
        require(width >= 0 && height >= 0) { "Mask dimensions must be non-negative" }
        require(data.size >= width * height) {
            "Mask data holds ${data.size} bytes but $width x $height requires ${width * height}"
        }
    }

    /** True when `(x, y)` is in bounds and its byte is non-zero. */
    fun get(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && data[y * width + x].toInt() != 0

    /** Marks `(x, y)` as foreground. Out-of-range coordinates are ignored. */
    fun set(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) data[y * width + x] = FOREGROUND
    }

    /** Number of foreground pixels. */
    fun foregroundCount(): Int {
        var count = 0
        for (b in data) if (b.toInt() != 0) count++
        return count
    }

    companion object {
        const val FOREGROUND: Byte = 1

        /**
         * Builds a mask from floating-point values (typically segmentation probabilities in
         * `[0, 1]`). A value is foreground when `value >= threshold`.
         */
        fun fromFloatArray(width: Int, height: Int, values: FloatArray, threshold: Float): BinaryMask {
            require(values.size >= width * height) {
                "Value array holds ${values.size} entries but $width x $height requires ${width * height}"
            }
            val size = width * height
            val bytes = ByteArray(size)
            for (i in 0 until size) {
                if (values[i] >= threshold) bytes[i] = FOREGROUND
            }
            return BinaryMask(width, height, bytes)
        }

        /** Builds a mask by copying [values] (non-zero preserved as foreground). */
        fun fromBytes(width: Int, height: Int, values: ByteArray): BinaryMask {
            require(values.size >= width * height) {
                "Value array holds ${values.size} bytes but $width x $height requires ${width * height}"
            }
            return BinaryMask(width, height, values.copyOf(width * height))
        }

        /**
         * Rasterises [quad] into a solid mask. **Intended for tests only** — it lets test suites
         * build known ground-truth shapes without shipping image assets.
         *
         * A pixel is foreground when its centre `(x + 0.5, y + 0.5)` falls inside the quad
         * (points exactly on an edge count as inside).
         */
        fun filledQuad(width: Int, height: Int, quad: Quad): BinaryMask {
            val bytes = ByteArray(width * height)
            val polygon = listOf(
                quad.topLeft.toPointF(),
                quad.topRight.toPointF(),
                quad.bottomRight.toPointF(),
                quad.bottomLeft.toPointF()
            )
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (pointInPolygon(PointF(x + 0.5f, y + 0.5f), polygon)) {
                        bytes[y * width + x] = FOREGROUND
                    }
                }
            }
            return BinaryMask(width, height, bytes)
        }
    }
}
