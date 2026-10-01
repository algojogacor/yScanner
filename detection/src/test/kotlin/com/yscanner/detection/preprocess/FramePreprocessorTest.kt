package com.yscanner.detection.preprocess

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.model.ModelDescriptor
import com.yscanner.detection.model.ModelDType
import com.yscanner.detection.model.Normalization
import com.yscanner.detection.model.OutputKind
import org.junit.Test

class FramePreprocessorTest {

    private val preprocessor = DefaultFramePreprocessor()

    /**
     * Builds a square frame whose only bright luma pixel is at [markerX],[markerY].
     * U and V are neutral (128) so the RGB conversion is a pure luma response.
     */
    private fun frameWithMarker(
        size: Int,
        markerX: Int,
        markerY: Int,
        rotation: Int,
        bright: Int = 235,
        dark: Int = 16
    ): FrameData {
        val y = ByteArray(size * size) { dark.toByte() }
        y[markerY * size + markerX] = bright.toByte()
        val chroma = ByteArray(size * size / 4) { 128.toByte() }
        return FrameData.fromPlanar(size, size, rotation, y, chroma, chroma)
    }

    private fun descriptor(
        size: Int,
        dtype: ModelDType = ModelDType.FLOAT32,
        normalization: Normalization = Normalization.UNIT
    ) = ModelDescriptor(
        id = "t",
        version = "1",
        inputWidth = size,
        inputHeight = size,
        dtype = dtype,
        normalization = normalization,
        outputKind = OutputKind.MASK,
        outputWidth = size,
        outputHeight = size
    )

    /** Reads the red channel of output pixel (x, y) from a packed RGB float tensor. */
    private fun redAt(buffer: java.nio.ByteBuffer, size: Int, x: Int, y: Int): Float {
        val floats = buffer.asFloatBuffer()
        val base = (y * size + x) * 3
        return floats.get(base)
    }

    /** Finds the brightest red-channel pixel in the output tensor. */
    private fun brightestPixel(buffer: java.nio.ByteBuffer, size: Int): Pair<Int, Int> {
        val floats = buffer.asFloatBuffer()
        var best = -1f
        var bestX = 0
        var bestY = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = floats.get((y * size + x) * 3)
                if (v > best) {
                    best = v
                    bestX = x
                    bestY = y
                }
            }
        }
        return bestX to bestY
    }

    @Test
    fun `output size matches descriptor`() {
        val frame = frameWithMarker(8, 0, 0, rotation = 0)
        val input = preprocessor.preprocess(frame, descriptor(8))

        assertThat(input.width).isEqualTo(8)
        assertThat(input.height).isEqualTo(8)
        assertThat(input.channels).isEqualTo(3)
        assertThat(input.data.capacity()).isEqualTo(8 * 8 * 3 * 4)
    }

    @Test
    fun `zero rotation keeps the marker at the origin`() {
        val frame = frameWithMarker(8, 0, 0, rotation = 0)
        val input = preprocessor.preprocess(frame, descriptor(8))

        assertThat(brightestPixel(input.data, 8)).isEqualTo(0 to 0)
    }

    @Test
    fun `90 degree rotation moves the top-left marker to the top-right`() {
        // Rotating the raw frame 90 degrees clockwise should carry the raw
        // top-left pixel to the upright image's top-right corner.
        val frame = frameWithMarker(8, 0, 0, rotation = 90)
        val input = preprocessor.preprocess(frame, descriptor(8))

        assertThat(brightestPixel(input.data, 8)).isEqualTo(7 to 0)
    }

    @Test
    fun `180 degree rotation moves the marker to the opposite corner`() {
        val frame = frameWithMarker(8, 0, 0, rotation = 180)
        val input = preprocessor.preprocess(frame, descriptor(8))

        assertThat(brightestPixel(input.data, 8)).isEqualTo(7 to 7)
    }

    @Test
    fun `270 degree rotation moves the top-left marker to the bottom-left`() {
        val frame = frameWithMarker(8, 0, 0, rotation = 270)
        val input = preprocessor.preprocess(frame, descriptor(8))

        assertThat(brightestPixel(input.data, 8)).isEqualTo(0 to 7)
    }

    @Test
    fun `unit normalization scales 0-255 into 0-1`() {
        val frame = frameWithMarker(4, 0, 0, rotation = 0, bright = 235, dark = 16)
        val input = preprocessor.preprocess(frame, descriptor(4, normalization = Normalization.UNIT))

        val bright = redAt(input.data, 4, 0, 0)
        val dark = redAt(input.data, 4, 1, 1)

        assertThat(bright).isGreaterThan(0.85f)
        assertThat(bright).isAtMost(1f)
        assertThat(dark).isLessThan(0.05f)
    }

    @Test
    fun `no normalization keeps raw 0-255 scale`() {
        val frame = frameWithMarker(4, 0, 0, rotation = 0, bright = 235, dark = 16)
        val input = preprocessor.preprocess(frame, descriptor(4, normalization = Normalization.NONE))

        assertThat(redAt(input.data, 4, 0, 0)).isGreaterThan(200f)
        assertThat(redAt(input.data, 4, 1, 1)).isLessThan(30f)
    }

    @Test
    fun `uint8 dtype packs one byte per channel`() {
        val frame = frameWithMarker(4, 0, 0, rotation = 0)
        val input = preprocessor.preprocess(frame, descriptor(4, dtype = ModelDType.UINT8))

        assertThat(input.data.capacity()).isEqualTo(4 * 4 * 3)
        // Top-left pixel should be bright in all three channels.
        assertThat(input.data.get(0).toInt() and 0xFF).isGreaterThan(200)
    }

    @Test
    fun `downscaling to a smaller tensor preserves the marker quadrant`() {
        val frame = frameWithMarker(16, 0, 0, rotation = 0)
        val input = preprocessor.preprocess(frame, descriptor(8))

        val (x, y) = brightestPixel(input.data, 8)
        assertThat(x).isLessThan(4)
        assertThat(y).isLessThan(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frame rejects an invalid rotation`() {
        val y = ByteArray(16)
        val c = ByteArray(4)
        FrameData.fromPlanar(4, 4, rotationDegrees = 45, y = y, u = c, v = c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frame rejects undersized planes`() {
        FrameData.fromPlanar(4, 4, 0, y = ByteArray(4), u = ByteArray(4), v = ByteArray(4))
    }

    @Test
    fun `upright dimensions swap for quarter turns`() {
        val y = ByteArray(8 * 4)
        val c = ByteArray(4 * 2)
        val landscape = FrameData.fromPlanar(8, 4, rotationDegrees = 0, y = y, u = c, v = c)
        val rotated = FrameData.fromPlanar(8, 4, rotationDegrees = 90, y = y, u = c, v = c)

        assertThat(landscape.uprightWidth).isEqualTo(8)
        assertThat(landscape.uprightHeight).isEqualTo(4)
        assertThat(rotated.uprightWidth).isEqualTo(4)
        assertThat(rotated.uprightHeight).isEqualTo(8)
    }
}
