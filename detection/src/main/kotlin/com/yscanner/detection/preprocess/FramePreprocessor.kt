package com.yscanner.detection.preprocess

import com.yscanner.detection.model.ModelDType
import com.yscanner.detection.model.ModelDescriptor
import com.yscanner.detection.model.ModelInput
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Converts a camera [FrameData] into a model-ready [ModelInput].
 *
 * This is the seam that keeps model choice out of the frame pipeline: a model
 * declares its input geometry and normalisation in its [ModelDescriptor], and
 * the preprocessor honours it. Adding a model with a different input size or
 * colour convention therefore requires no pipeline change.
 */
interface FramePreprocessor {
    fun preprocess(frame: FrameData, descriptor: ModelDescriptor): ModelInput
}

/**
 * Single-pass YUV_420_888 → RGB preprocessor with rotation and scaling.
 *
 * Design note (PRD memory rules): rotation and resize are fused into **one**
 * sampling pass. The naive implementation — decode to a full-resolution RGB
 * bitmap, rotate it, then scale it — allocates two full-resolution ARGB buffers
 * per frame (~48 MB each on a 12 MP sensor) and would blow the memory budget at
 * 15 FPS. Here the only allocation is the model's own input tensor, which is
 * orders of magnitude smaller (e.g. 256×256×3 floats ≈ 786 KB).
 *
 * Colour conversion uses the BT.601 **limited-range** matrix, which is what
 * Android's `YUV_420_888` camera output conventionally carries.
 */
class DefaultFramePreprocessor : FramePreprocessor {

    override fun preprocess(frame: FrameData, descriptor: ModelDescriptor): ModelInput {
        val outW = descriptor.inputWidth
        val outH = descriptor.inputHeight
        val channels = descriptor.inputChannels
        require(channels == 3) { "only 3-channel RGB models are supported, got $channels" }

        val values = FloatArray(outW * outH * channels)
        val uprightW = frame.uprightWidth
        val uprightH = frame.uprightHeight

        var index = 0
        for (oy in 0 until outH) {
            // Map output row to a continuous upright-image row (pixel centres).
            val uy = ((oy + 0.5f) * uprightH / outH) - 0.5f
            for (ox in 0 until outW) {
                val ux = ((ox + 0.5f) * uprightW / outW) - 0.5f

                // Inverse-rotate the upright sample back into raw frame space.
                val rx: Float
                val ry: Float
                when (frame.rotationDegrees) {
                    0 -> {
                        rx = ux; ry = uy
                    }
                    90 -> {
                        // Raw rotated 90° clockwise yields the upright image.
                        rx = uy; ry = (frame.height - 1) - ux
                    }
                    180 -> {
                        rx = (frame.width - 1) - ux; ry = (frame.height - 1) - uy
                    }
                    else -> {
                        // 270: raw rotated 270° clockwise (== 90° CCW).
                        rx = (frame.width - 1) - uy; ry = ux
                    }
                }

                val y = sampleLuma(frame, rx, ry)
                val u = sampleChroma(frame.uPlane, frame, rx, ry)
                val v = sampleChroma(frame.vPlane, frame, rx, ry)

                val rgb = yuvToRgb(y, u, v)

                values[index++] = applyNormalisation(rgb[0], 0, descriptor)
                values[index++] = applyNormalisation(rgb[1], 1, descriptor)
                values[index++] = applyNormalisation(rgb[2], 2, descriptor)
            }
        }

        return ModelInput(pack(values, descriptor.dtype), descriptor)
    }

    /** Bilinear luma sample. Falls back to the nearest in-range pixel at edges. */
    private fun sampleLuma(frame: FrameData, x: Float, y: Float): Float {
        val x0 = floorClamped(x, frame.width)
        val y0 = floorClamped(y, frame.height)
        val x1 = (x0 + 1).coerceAtMost(frame.width - 1)
        val y1 = (y0 + 1).coerceAtMost(frame.height - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)

        val p = frame.yPlane
        val top = p.byteAt(x0, y0) * (1 - fx) + p.byteAt(x1, y0) * fx
        val bottom = p.byteAt(x0, y1) * (1 - fx) + p.byteAt(x1, y1) * fx
        return top * (1 - fy) + bottom * fy
    }

    /** Bilinear chroma sample. Chroma planes are half resolution. */
    private fun sampleChroma(plane: PlaneData, frame: FrameData, x: Float, y: Float): Float {
        val cw = (frame.width + 1) / 2
        val ch = (frame.height + 1) / 2
        val cx = x / 2f
        val cy = y / 2f
        val x0 = floorClamped(cx, cw)
        val y0 = floorClamped(cy, ch)
        val x1 = (x0 + 1).coerceAtMost(cw - 1)
        val y1 = (y0 + 1).coerceAtMost(ch - 1)
        val fx = (cx - x0).coerceIn(0f, 1f)
        val fy = (cy - y0).coerceIn(0f, 1f)

        val top = plane.byteAt(x0, y0) * (1 - fx) + plane.byteAt(x1, y0) * fx
        val bottom = plane.byteAt(x0, y1) * (1 - fx) + plane.byteAt(x1, y1) * fx
        return top * (1 - fy) + bottom * fy
    }

    private fun floorClamped(value: Float, limit: Int): Int {
        val f = kotlin.math.floor(value).toInt()
        return f.coerceIn(0, limit - 1)
    }

    /**
     * BT.601 limited-range YUV → RGB.
     * Inputs are 0..255; output is 0..255.
     */
    private fun yuvToRgb(y: Float, u: Float, v: Float): FloatArray {
        val yScaled = (y - 16f) * (255f / 219f)
        val uCentred = u - 128f
        val vCentred = v - 128f
        val r = yScaled + 1.402f * vCentred
        val g = yScaled - 0.344136f * uCentred - 0.714136f * vCentred
        val b = yScaled + 1.772f * uCentred
        return floatArrayOf(
            r.coerceIn(0f, 255f),
            g.coerceIn(0f, 255f),
            b.coerceIn(0f, 255f)
        )
    }

    private fun applyNormalisation(value: Float, channel: Int, descriptor: ModelDescriptor): Float {
        val n = descriptor.normalization
        var out = value * n.scale
        if (n.mean.isNotEmpty()) out -= n.mean[channel]
        if (n.std.isNotEmpty() && n.std[channel] != 0f) out /= n.std[channel]
        return out
    }

    private fun pack(values: FloatArray, dtype: ModelDType): ByteBuffer {
        val buffer = when (dtype) {
            ModelDType.FLOAT32 -> ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            ModelDType.UINT8 -> ByteBuffer.allocateDirect(values.size).order(ByteOrder.nativeOrder())
        }
        when (dtype) {
            ModelDType.FLOAT32 -> values.forEach { buffer.putFloat(it) }
            ModelDType.UINT8 -> values.forEach { buffer.put((it * 255f).roundToInt().coerceIn(0, 255).toByte()) }
        }
        buffer.rewind()
        return buffer
    }
}
