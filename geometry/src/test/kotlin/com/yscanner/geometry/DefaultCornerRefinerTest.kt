package com.yscanner.geometry

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.image.ImageRect
import com.yscanner.domain.image.ImageRegion
import com.yscanner.domain.image.ImageSource
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Test
import kotlin.math.hypot

/**
 * Tests for the full-resolution [DefaultCornerRefiner].
 *
 * These use synthetic images rather than device captures. The happy-path image is deliberately
 * placed well away from the origin (x around 800..1600, y around 600..1400) so that any confusion
 * between source-image and ROI-local coordinates produces a visibly wrong result instead of
 * accidentally working.
 */
class DefaultCornerRefinerTest {

    private val truth = Quad(
        topLeft = Corner(800f, 600f),
        topRight = Corner(1600f, 600f),
        bottomRight = Corner(1600f, 1400f),
        bottomLeft = Corner(800f, 1400f)
    )

    private fun quadPolygon(quad: Quad): List<PointF> = listOf(
        quad.topLeft.toPointF(),
        quad.topRight.toPointF(),
        quad.bottomRight.toPointF(),
        quad.bottomLeft.toPointF()
    )

    private fun distance(a: Corner, b: Corner): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    // ------------------------------------------------------------ fakes

    /**
     * A fake [ImageSource] whose pixels are produced by a per-rect script.
     *
     * It clamps exactly like a real decoder: a partly-out-of-bounds request returns the
     * **intersection** of the request and the image, not a padded region. Returning `null` from the
     * script simulates a decode miss.
     */
    private class ScriptedImageSource(
        override val width: Int,
        override val height: Int,
        private val content: (ImageRect) -> FloatArray?
    ) : ImageSource {
        override fun decodeRegion(rect: ImageRect): ImageRegion? {
            val clamped = rect.clampTo(width, height) ?: return null
            val pixels = content(clamped) ?: return null
            check(pixels.size == clamped.width * clamped.height) {
                "scripted content has ${pixels.size} entries but ${clamped.width}x${clamped.height} needs " +
                    "${clamped.width * clamped.height}"
            }
            return ImageRegion(clamped.left, clamped.top, clamped.width, clamped.height, pixels)
        }

        override fun close() = Unit
    }

    /** Bright inside [polygon] (0.9) on a dark background (0.1), evaluated at pixel centres. */
    private fun polygonSource(width: Int, height: Int, polygon: List<PointF>): ImageSource =
        ScriptedImageSource(width, height) { rect ->
            val pixels = FloatArray(rect.width * rect.height)
            for (y in 0 until rect.height) {
                for (x in 0 until rect.width) {
                    val inside = pointInPolygon(
                        PointF(rect.left + x + 0.5f, rect.top + y + 0.5f),
                        polygon
                    )
                    pixels[y * rect.width + x] = if (inside) 0.9f else 0.1f
                }
            }
            pixels
        }

    private fun constantSource(width: Int, height: Int, value: Float): ImageSource =
        ScriptedImageSource(width, height) { rect ->
            FloatArray(rect.width * rect.height) { value }
        }

    // ------------------------------------------------------------ happy path

    @Test
    fun `refinement pulls every perturbed corner closer to the truth`() {
        val approx = Quad(
            topLeft = Corner(812f, 588f),
            topRight = Corner(1590f, 608f),
            bottomRight = Corner(1608f, 1392f),
            bottomLeft = Corner(790f, 1408f)
        )
        val source = polygonSource(2000, 2000, quadPolygon(truth))

        val refined = DefaultCornerRefiner().refine(approx, source)

        assertThat(refined.isConvex()).isTrue()
        val truthCorners = listOf(truth.topLeft, truth.topRight, truth.bottomRight, truth.bottomLeft)
        val before = listOf(approx.topLeft, approx.topRight, approx.bottomRight, approx.bottomLeft)
        val after = listOf(refined.topLeft, refined.topRight, refined.bottomRight, refined.bottomLeft)
        for (i in 0 until 4) {
            assertThat(distance(after[i], truthCorners[i])).isLessThan(distance(before[i], truthCorners[i]))
        }
    }

    @Test
    fun `corners that are already exact stay put`() {
        val source = polygonSource(2000, 2000, quadPolygon(truth))

        val refined = DefaultCornerRefiner().refine(truth, source)

        // Hard synthetic edges carry a sub-pixel nearest-integer bias of up to about one pixel.
        assertThat(distance(refined.topLeft, truth.topLeft)).isLessThan(2.0)
        assertThat(distance(refined.topRight, truth.topRight)).isLessThan(2.0)
        assertThat(distance(refined.bottomRight, truth.bottomRight)).isLessThan(2.0)
        assertThat(distance(refined.bottomLeft, truth.bottomLeft)).isLessThan(2.0)
    }

    // ------------------------------------------------------------ fallbacks

    @Test
    fun `a flat image returns the input unchanged`() {
        // No edges anywhere: every gradient is below the threshold, so no edge point is accepted.
        // This is the test that fails if the perpendicular search takes an unconditional argmax.
        val source = constantSource(2000, 2000, 0.5f)

        assertThat(DefaultCornerRefiner().refine(truth, source)).isEqualTo(truth)
    }

    @Test
    fun `a decode miss returns the input unchanged`() {
        val source = ScriptedImageSource(2000, 2000) { null }

        assertThat(DefaultCornerRefiner().refine(truth, source)).isEqualTo(truth)
    }

    @Test
    fun `a corner near the image border does not throw and stays finite`() {
        val borderQuad = Quad(
            topLeft = Corner(10f, 10f),
            topRight = Corner(190f, 10f),
            bottomRight = Corner(190f, 190f),
            bottomLeft = Corner(10f, 190f)
        )
        val source = polygonSource(200, 200, quadPolygon(borderQuad))

        val refined = DefaultCornerRefiner().refine(borderQuad, source)

        for (corner in listOf(refined.topLeft, refined.topRight, refined.bottomRight, refined.bottomLeft)) {
            assertThat(corner.x.isFinite()).isTrue()
            assertThat(corner.y.isFinite()).isTrue()
        }
    }

    // ------------------------------------------------------------ bounds and guards

    @Test
    fun `displacement is clamped to the configured bound`() {
        val anchor = PointF(800f, 600f)
        // Crafted edges 60 px away on both axes around the top-left corner: the raw intersection
        // would sit ~85 px away, far beyond the 5 px bound.
        val source = ScriptedImageSource(2000, 2000) { rect ->
            val centreX = rect.left + rect.width / 2.0
            val centreY = rect.top + rect.height / 2.0
            val pixels = FloatArray(rect.width * rect.height)
            if (hypot(centreX - anchor.x, centreY - anchor.y) >= 20.0) {
                pixels.fill(0.5f)
            } else {
                for (y in 0 until rect.height) {
                    for (x in 0 until rect.width) {
                        val sx = rect.left + x + 0.5
                        val sy = rect.top + y + 0.5
                        pixels[y * rect.width + x] = if (sx >= 860.0 || sy >= 660.0) 0.9f else 0.1f
                    }
                }
            }
            pixels
        }
        val config = CornerRefinerConfig(roiSizePx = 320, searchBandPx = 80, maxDisplacementPx = 5f)

        val refined = DefaultCornerRefiner(config).refine(truth, source)

        val moved = hypot(
            (refined.topLeft.x - truth.topLeft.x).toDouble(),
            (refined.topLeft.y - truth.topLeft.y).toDouble()
        )
        // The clamp must have engaged (not a no-op) and must not have been exceeded.
        assertThat(moved).isAtLeast(4.9)
        assertThat(moved).isAtMost(5.01)

        val originals = listOf(truth.topLeft, truth.topRight, truth.bottomRight, truth.bottomLeft)
        val results = listOf(refined.topLeft, refined.topRight, refined.bottomRight, refined.bottomLeft)
        for (i in 0 until 4) {
            assertThat(distance(results[i], originals[i])).isAtMost(5.01)
        }
    }

    @Test
    fun `a refinement that would fold the quad returns the input unchanged`() {
        val anchor = PointF(800f, 600f)
        // The top-left corner's ROI contains a strong horizontal edge at y = 880 and a vertical
        // edge at x = 1000, so the raw intersection would drag topLeft to (1000, 880) — below the
        // bottom corners. The other corners see a flat region and do not move.
        val source = ScriptedImageSource(2000, 2000) { rect ->
            val centreX = rect.left + rect.width / 2.0
            val centreY = rect.top + rect.height / 2.0
            val pixels = FloatArray(rect.width * rect.height)
            if (hypot(centreX - anchor.x, centreY - anchor.y) >= 20.0) {
                pixels.fill(0.5f)
            } else {
                for (y in 0 until rect.height) {
                    for (x in 0 until rect.width) {
                        val sx = rect.left + x + 0.5
                        val sy = rect.top + y + 0.5
                        pixels[y * rect.width + x] = if (sx >= 1000.0 || sy >= 880.0) 0.9f else 0.1f
                    }
                }
            }
            pixels
        }
        val config = CornerRefinerConfig(roiSizePx = 1000, searchBandPx = 300, maxDisplacementPx = 1000f)
        val smallQuad = Quad(
            topLeft = Corner(800f, 600f),
            topRight = Corner(880f, 600f),
            bottomRight = Corner(880f, 680f),
            bottomLeft = Corner(800f, 680f)
        )
        assertThat(smallQuad.isConvex()).isTrue()

        val refined = DefaultCornerRefiner(config).refine(smallQuad, source)

        // Prove the guard was needed rather than the test passing for an unrelated reason.
        val rawFold = Quad(
            topLeft = Corner(1000f, 880f),
            topRight = smallQuad.topRight,
            bottomRight = smallQuad.bottomRight,
            bottomLeft = smallQuad.bottomLeft
        )
        assertThat(rawFold.isConvex()).isFalse()
        assertThat(refined).isEqualTo(smallQuad)
    }

    // ------------------------------------------------------------ confidence and degenerate input

    @Test
    fun `confidence values are preserved while corners move`() {
        val approx = Quad(
            topLeft = Corner(812f, 588f, 0.9f),
            topRight = Corner(1590f, 608f, 0.8f),
            bottomRight = Corner(1608f, 1392f, 0.7f),
            bottomLeft = Corner(790f, 1408f, 0.6f)
        )
        val source = polygonSource(2000, 2000, quadPolygon(truth))

        val refined = DefaultCornerRefiner().refine(approx, source)

        assertThat(refined.topLeft.confidence).isEqualTo(0.9f)
        assertThat(refined.topRight.confidence).isEqualTo(0.8f)
        assertThat(refined.bottomRight.confidence).isEqualTo(0.7f)
        assertThat(refined.bottomLeft.confidence).isEqualTo(0.6f)
        // The confidences must be preserved on a quad that actually refined, not on a no-op.
        assertThat(distance(refined.topLeft, approx.topLeft)).isGreaterThan(1.0)
    }

    @Test
    fun `degenerate input is returned unchanged`() {
        val source = polygonSource(2000, 2000, quadPolygon(truth))

        val zeroArea = Quad(
            topLeft = Corner(5f, 5f),
            topRight = Corner(5f, 5f),
            bottomRight = Corner(5f, 5f),
            bottomLeft = Corner(5f, 5f)
        )
        assertThat(DefaultCornerRefiner().refine(zeroArea, source)).isEqualTo(zeroArea)

        val emptySource = ScriptedImageSource(0, 0) { null }
        assertThat(DefaultCornerRefiner().refine(truth, emptySource)).isEqualTo(truth)
    }
}
