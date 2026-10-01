package com.yscanner.data.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArgbLuminanceTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `pure black maps to 0f`() {
        assertThat(argbToLuminance(black)).isWithin(0.001f).of(0f)
    }

    @Test
    fun `pure white maps to 1f`() {
        assertThat(argbToLuminance(white)).isWithin(0.001f).of(1f)
    }

    @Test
    fun `pure red matches the Rec 601 red coefficient`() {
        assertThat(argbToLuminance(red)).isWithin(0.01f).of(0.299f)
    }

    @Test
    fun `pure green matches the Rec 601 green coefficient`() {
        assertThat(argbToLuminance(green)).isWithin(0.01f).of(0.587f)
    }

    @Test
    fun `pure blue matches the Rec 601 blue coefficient`() {
        assertThat(argbToLuminance(blue)).isWithin(0.01f).of(0.114f)
    }

    @Test
    fun `alpha is ignored so transparent and opaque white are identical`() {
        val transparentWhite = 0x00FFFFFF
        assertThat(argbToLuminance(transparentWhite)).isEqualTo(argbToLuminance(white))
    }

    @Test
    fun `batch conversion matches the single pixel function element by element`() {
        val pixels = intArrayOf(black, white, red, green, blue, 0xFF123456.toInt(), 0x00ABCDEF.toInt())
        val out = FloatArray(pixels.size)

        argbToLuminance(pixels, out)

        for (i in pixels.indices) {
            assertThat(out[i]).isEqualTo(argbToLuminance(pixels[i]))
        }
    }

    @Test
    fun `count smaller than the array converts only the prefix and leaves the tail untouched`() {
        val pixels = intArrayOf(white, white, white, white)
        val out = FloatArray(4) { -1f }

        argbToLuminance(pixels, out, count = 2)

        assertThat(out[0]).isWithin(0.001f).of(1f)
        assertThat(out[1]).isWithin(0.001f).of(1f)
        assertThat(out[2]).isEqualTo(-1f)
        assertThat(out[3]).isEqualTo(-1f)
    }

    @Test
    fun `count of zero leaves the output untouched`() {
        val out = FloatArray(3) { -1f }

        argbToLuminance(intArrayOf(white, white, white), out, count = 0)

        assertThat(out).isEqualTo(floatArrayOf(-1f, -1f, -1f))
    }

    @Test
    fun `count larger than either array does not throw or read out of bounds`() {
        val pixels = intArrayOf(white, white)
        val out = FloatArray(5) { -1f }

        argbToLuminance(pixels, out, count = 99)

        assertThat(out[0]).isWithin(0.001f).of(1f)
        assertThat(out[1]).isWithin(0.001f).of(1f)
        assertThat(out[2]).isEqualTo(-1f)
        assertThat(out[3]).isEqualTo(-1f)
        assertThat(out[4]).isEqualTo(-1f)
    }

    @Test
    fun `empty input is a no-op`() {
        val out = FloatArray(0)

        argbToLuminance(IntArray(0), out)

        assertThat(out).isEmpty()
    }
}
