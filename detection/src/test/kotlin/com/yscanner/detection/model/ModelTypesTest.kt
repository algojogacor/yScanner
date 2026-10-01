package com.yscanner.detection.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelTypesTest {

    private fun descriptor(
        kind: OutputKind = OutputKind.MASK,
        w: Int = 8,
        h: Int = 8
    ) = ModelDescriptor(
        id = "test-model",
        version = "1",
        inputWidth = w,
        inputHeight = h,
        outputKind = kind,
        outputWidth = w,
        outputHeight = h
    )

    @Test
    fun `descriptor exposes element counts`() {
        val d = descriptor(w = 16, h = 32)
        assertThat(d.inputElementCount).isEqualTo(16 * 32 * 3)
        assertThat(d.outputElementCount).isEqualTo(16 * 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `descriptor rejects blank id`() {
        ModelDescriptor(id = "  ", version = "1", inputWidth = 8, inputHeight = 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `descriptor rejects non positive dimensions`() {
        ModelDescriptor(id = "m", version = "1", inputWidth = 0, inputHeight = 8)
    }

    @Test
    fun `mask output requires one plane worth of values`() {
        val d = descriptor(OutputKind.MASK, 4, 4)
        // 16 values is exactly right for a single 4x4 plane.
        SegmentationOutput(d, FloatArray(16), true, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mask output rejects wrong sized array`() {
        val d = descriptor(OutputKind.MASK, 4, 4)
        SegmentationOutput(d, FloatArray(15), true, 0)
    }

    @Test
    fun `corner heatmap output requires four planes`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 4, 4)
        val output = SegmentationOutput(d, FloatArray(4 * 16), true, 0)
        assertThat(output.planeCount).isEqualTo(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corner heatmap rejects single plane sized array`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 4, 4)
        SegmentationOutput(d, FloatArray(16), true, 0)
    }

    @Test
    fun `at reads the correct plane and is bounds safe`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 2, 2)
        val values = FloatArray(4 * 4)
        // plane 2, pixel (1,1) -> index 2*4 + 1*2 + 1 = 11
        values[11] = 0.75f
        val output = SegmentationOutput(d, values, true, 0)

        assertThat(output.at(1, 1, plane = 2)).isEqualTo(0.75f)
        assertThat(output.at(0, 0, plane = 2)).isEqualTo(0f)
        assertThat(output.at(-1, 0, plane = 2)).isEqualTo(0f)
        assertThat(output.at(0, 0, plane = 9)).isEqualTo(0f)
        assertThat(output.at(99, 99, plane = 0)).isEqualTo(0f)
    }

    @Test
    fun `plane returns an independent copy`() {
        val d = descriptor(OutputKind.CORNER_HEATMAP, 2, 2)
        val values = FloatArray(16)
        values[5] = 1f // plane 1, index 1
        val output = SegmentationOutput(d, values, true, 0)

        val plane1 = output.plane(1)
        assertThat(plane1.size).isEqualTo(4)
        assertThat(plane1[1]).isEqualTo(1f)

        plane1[1] = 0f
        assertThat(output.at(1, 0, plane = 1)).isEqualTo(1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plane rejects out of range index`() {
        val d = descriptor(OutputKind.MASK, 2, 2)
        SegmentationOutput(d, FloatArray(4), true, 0).plane(3)
    }

    @Test
    fun `normalization equality is value based`() {
        val a = Normalization(scale = 0.5f, mean = floatArrayOf(1f, 2f, 3f), std = floatArrayOf(1f, 1f, 1f))
        val b = Normalization(scale = 0.5f, mean = floatArrayOf(1f, 2f, 3f), std = floatArrayOf(1f, 1f, 1f))
        val c = Normalization(scale = 0.6f, mean = floatArrayOf(1f, 2f, 3f), std = floatArrayOf(1f, 1f, 1f))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalization rejects zero std`() {
        Normalization(scale = 1f, mean = floatArrayOf(0f, 0f, 0f), std = floatArrayOf(1f, 0f, 1f))
    }
}
