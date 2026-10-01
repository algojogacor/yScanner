package com.yscanner.detection.postprocess

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.model.ModelDescriptor
import com.yscanner.detection.model.OutputKind
import com.yscanner.detection.model.SegmentationOutput
import org.junit.Test

class SegmentationPostprocessorTest {

    private val postprocessor = DefaultSegmentationPostprocessor()

    private fun descriptor(kind: OutputKind, w: Int = 4, h: Int = 4) = ModelDescriptor(
        id = "t",
        version = "1",
        inputWidth = w,
        inputHeight = h,
        outputKind = kind,
        outputWidth = w,
        outputHeight = h
    )

    @Test
    fun `mask output passes through unchanged`() {
        val d = descriptor(OutputKind.MASK)
        val values = floatArrayOf(
            0.1f, 0.2f, 0.3f, 0.4f,
            0.5f, 0.6f, 0.7f, 0.8f,
            0.9f, 1.0f, 0.0f, 0.15f,
            0.25f, 0.35f, 0.45f, 0.55f
        )
        val map = postprocessor.toProbabilityMap(SegmentationOutput(d, values, true, 0))

        assertThat(map.width).isEqualTo(4)
        assertThat(map.height).isEqualTo(4)
        assertThat(map.at(0, 0)).isEqualTo(0.1f)
        // Row-major: index 2 on row 0 is 0.3; 0.9 sits at (x=0, y=2).
        assertThat(map.at(2, 0)).isEqualTo(0.3f)
        assertThat(map.at(0, 2)).isEqualTo(0.9f)
        assertThat(map.max()).isEqualTo(1.0f)
    }

    @Test
    fun `out of range values are clamped`() {
        val d = descriptor(OutputKind.MASK, 2, 2)
        val values = floatArrayOf(-5f, 2f, 0.5f, 0.5f)
        val map = postprocessor.toProbabilityMap(SegmentationOutput(d, values, true, 0))

        assertThat(map.at(0, 0)).isEqualTo(0f)
        assertThat(map.at(1, 0)).isEqualTo(1f)
    }

    @Test
    fun `sigmoid mode maps logits into probabilities`() {
        val sigmoid = DefaultSegmentationPostprocessor(applySigmoid = true)
        val d = descriptor(OutputKind.MASK, 2, 2)
        // logit 0 -> 0.5; a large positive logit -> ~1
        val map = sigmoid.toProbabilityMap(SegmentationOutput(d, floatArrayOf(0f, 10f, -10f, 0f), true, 0))

        assertThat(map.at(0, 0)).isWithin(0.001f).of(0.5f)
        assertThat(map.at(1, 0)).isGreaterThan(0.99f)
        assertThat(map.at(0, 1)).isLessThan(0.01f)
    }

    @Test
    fun `corner heatmap reduces to a single combined plane`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 4, 4)
        val planeSize = 16
        val values = FloatArray(planeSize * 4)
        // Put a strong impulse in plane 0 at (0,0) and plane 3 at (3,3).
        values[0 * planeSize + 0] = 0.8f
        values[3 * planeSize + 15] = 0.6f

        val map = postprocessor.toProbabilityMap(SegmentationOutput(d, values, true, 0))

        assertThat(map.at(0, 0)).isEqualTo(0.8f)
        assertThat(map.at(3, 3)).isEqualTo(0.6f)
        assertThat(map.at(1, 1)).isEqualTo(0f)
    }

    @Test
    fun `decodeCorners recovers the four impulse locations in order`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 4, 4)
        val planeSize = 16
        val values = FloatArray(planeSize * 4)
        // TL(0,0) TR(3,0) BR(3,3) BL(0,3)
        values[0 * planeSize + (0 * 4 + 0)] = 0.9f
        values[1 * planeSize + (0 * 4 + 3)] = 0.8f
        values[2 * planeSize + (3 * 4 + 3)] = 0.7f
        values[3 * planeSize + (3 * 4 + 0)] = 0.6f

        val corners = postprocessor.decodeCorners(SegmentationOutput(d, values, true, 0))

        assertThat(corners).isNotNull()
        requireNotNull(corners)
        assertThat(corners.topLeft.x).isEqualTo(0f)
        assertThat(corners.topLeft.y).isEqualTo(0f)
        assertThat(corners.topRight.x).isEqualTo(3f)
        assertThat(corners.topRight.y).isEqualTo(0f)
        assertThat(corners.bottomRight.x).isEqualTo(3f)
        assertThat(corners.bottomRight.y).isEqualTo(3f)
        assertThat(corners.bottomLeft.x).isEqualTo(0f)
        assertThat(corners.bottomLeft.y).isEqualTo(3f)
        assertThat(corners.confidence).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun `decodeCorners returns null when no corner clears the peak threshold`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 4, 4)
        val values = FloatArray(16 * 4) { 0.01f }
        val result = postprocessor.decodeCorners(SegmentationOutput(d, values, true, 0), minPeak = 0.2f)

        assertThat(result).isNull()
    }

    @Test
    fun `decodeCorners returns null for a mask-shaped output`() {
        val d = descriptor(OutputKind.MASK)
        val result = postprocessor.decodeCorners(SegmentationOutput(d, FloatArray(16), true, 0))

        assertThat(result).isNull()
    }

    @Test
    fun `coverageAbove reports the foreground fraction`() {
        val d = descriptor(OutputKind.MASK, 2, 2)
        val map = postprocessor.toProbabilityMap(
            SegmentationOutput(d, floatArrayOf(0.9f, 0.1f, 0.8f, 0.2f), true, 0)
        )

        assertThat(map.coverageAbove(0.5f)).isWithin(0.001f).of(0.5f)
        assertThat(map.coverageAbove(0.95f)).isEqualTo(0f)
    }
}
