package com.yscanner.data.image

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.image.ImageRect
import org.junit.Test

/**
 * Tests for [regionFromDecodedPixels], the pure step of ROI decoding.
 *
 * The surrounding [FileImageSource] Android adapter cannot run here (no device, no emulator), so this
 * file pins the logic that would otherwise be untestable and that is most likely to be subtly wrong:
 * reconciling the rectangle we requested with the size the decoder actually returned.
 */
class FileImageSourceTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `an exact size match yields the requested origin and dimensions`() {
        val requested = ImageRect(10, 20, 12, 22) // 2x2, matching the decode
        val argb = IntArray(4) { white }

        val region = regionFromDecodedPixels(requested, 2, 2, argb)

        assertThat(region).isNotNull()
        assertThat(region!!.originX).isEqualTo(10)
        assertThat(region.originY).isEqualTo(20)
        assertThat(region.width).isEqualTo(2)
        assertThat(region.height).isEqualTo(2)
    }

    @Test
    fun `a decoded size smaller than requested shrinks the region without shifting its origin`() {
        // Requested an 8x8 region at (100, 200); the OEM decoder handed back only 3x3.
        val requested = ImageRect(100, 200, 108, 208)
        val argb = IntArray(3 * 3) { white }

        val region = regionFromDecodedPixels(requested, 3, 3, argb)

        assertThat(region).isNotNull()
        // The origin is the one we requested, NOT recomputed from the decoded size.
        assertThat(region!!.originX).isEqualTo(100)
        assertThat(region.originY).isEqualTo(200)
        // The dimensions are the decoded ones, so the region shrank rather than shifted.
        assertThat(region.width).isEqualTo(3)
        assertThat(region.height).isEqualTo(3)
    }

    @Test
    fun `a non-zero origin is propagated verbatim`() {
        val requested = ImageRect(37, 91, 38, 92)

        val region = regionFromDecodedPixels(requested, 1, 1, intArrayOf(white))

        assertThat(region).isNotNull()
        assertThat(region!!.originX).isEqualTo(37)
        assertThat(region.originY).isEqualTo(91)
        assertThat(region.toSourceX(0f)).isEqualTo(37f)
        assertThat(region.toSourceY(0f)).isEqualTo(91f)
    }

    @Test
    fun `a zero decoded dimension returns null`() {
        val requested = ImageRect(0, 0, 5, 5)

        assertThat(regionFromDecodedPixels(requested, 0, 5, IntArray(0))).isNull()
        assertThat(regionFromDecodedPixels(requested, 5, 0, IntArray(0))).isNull()
    }

    @Test
    fun `a negative decoded dimension returns null`() {
        val requested = ImageRect(0, 0, 5, 5)

        assertThat(regionFromDecodedPixels(requested, -1, 5, IntArray(0))).isNull()
        assertThat(regionFromDecodedPixels(requested, 5, -1, IntArray(0))).isNull()
    }

    @Test
    fun `argb shorter than the decoded area returns null`() {
        val requested = ImageRect(0, 0, 2, 2)

        assertThat(regionFromDecodedPixels(requested, 2, 2, IntArray(3))).isNull()
    }

    @Test
    fun `a luminance scratch buffer shorter than the decoded area returns null`() {
        val requested = ImageRect(0, 0, 2, 2)

        val region = regionFromDecodedPixels(requested, 2, 2, IntArray(4), FloatArray(3))

        assertThat(region).isNull()
    }

    @Test
    fun `luminance matches the single pixel conversion for every entry`() {
        val requested = ImageRect(0, 0, 2, 2)
        val argb = intArrayOf(black, white, 0xFF123456.toInt(), 0xFFABCDEF.toInt())

        val region = regionFromDecodedPixels(requested, 2, 2, argb)

        assertThat(region).isNotNull()
        assertThat(region!!.pixels.size).isEqualTo(4)
        for (i in argb.indices) {
            assertThat(region.pixels[i]).isEqualTo(argbToLuminance(argb[i]))
        }
    }

    @Test
    fun `pure black maps to 0f and pure white maps to 1f`() {
        val requested = ImageRect(0, 0, 2, 2)
        val argb = intArrayOf(black, white, black, white)

        val region = regionFromDecodedPixels(requested, 2, 2, argb)!!

        assertThat(region[0, 0]).isWithin(0.001f).of(0f)
        assertThat(region[1, 0]).isWithin(0.001f).of(1f)
        assertThat(region[0, 1]).isWithin(0.001f).of(0f)
        assertThat(region[1, 1]).isWithin(0.001f).of(1f)
    }
}
