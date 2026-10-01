package com.yscanner.detection.extract

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.postprocess.ProbabilityMap
import com.yscanner.domain.model.PointF
import org.junit.Test
import kotlin.math.hypot

/**
 * Tests for the detection→geometry seam.
 *
 * The coordinate-scaling assertions matter most: a segmentation model runs at a
 * fraction of camera resolution, so a quad returned in model space is silently
 * wrong for the camera overlay until it is scaled. A bug here produces an overlay
 * that looks plausible but is offset by a constant factor.
 */
class CandidateExtractorTest {

    private val extractor = DefaultCandidateExtractor()

    /** Builds a probability map with one or more filled rectangles at high probability. */
    private fun rectMap(
        width: Int,
        height: Int,
        rects: List<IntArray>, // each [x0, y0, x1, y1] inclusive
        inside: Float = 0.9f,
        outside: Float = 0.05f
    ): ProbabilityMap {
        val values = FloatArray(width * height) { outside }
        for (r in rects) {
            val (x0, y0, x1, y1) = r
            for (y in y0..y1) {
                for (x in x0..x1) {
                    if (x in 0 until width && y in 0 until height) values[y * width + x] = inside
                }
            }
        }
        return ProbabilityMap(width, height, values)
    }

    private fun distance(a: com.yscanner.domain.model.Corner, x: Float, y: Float): Float =
        hypot(a.x - x, a.y - y)

    // ------------------------------------------------------------ happy path

    @Test
    fun `extracts a single rectangle and scales it into frame space`() {
        // Model space 64x64 with a 32x32 block; frame space 640x480.
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))

        val candidates = extractor.extract(map, frameWidth = 640, frameHeight = 480)

        assertThat(candidates).hasSize(1)
        val quad = candidates[0].quad

        // scaleX = 640/64 = 10, scaleY = 480/64 = 7.5
        assertThat(distance(quad.topLeft, 160f, 120f)).isLessThan(3f)
        assertThat(distance(quad.topRight, 470f, 120f)).isLessThan(3f)
        assertThat(distance(quad.bottomRight, 470f, 352.5f)).isLessThan(3f)
        assertThat(distance(quad.bottomLeft, 160f, 352.5f)).isLessThan(3f)
    }

    @Test
    fun `scaling respects non-uniform aspect ratios`() {
        // A square block in model space must become a non-square quad when the
        // frame is not square — this catches a single-ratio scaling bug.
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))

        val candidates = extractor.extract(map, frameWidth = 640, frameHeight = 480)
        val quad = candidates[0].quad

        val width = hypot(
            (quad.topRight.x - quad.topLeft.x).toDouble(),
            (quad.topRight.y - quad.topLeft.y).toDouble()
        )
        val height = hypot(
            (quad.bottomLeft.x - quad.topLeft.x).toDouble(),
            (quad.bottomLeft.y - quad.topLeft.y).toDouble()
        )

        assertThat(width).isGreaterThan(height)
    }

    @Test
    fun `extracted quad is convex and correctly ordered`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))
        val quad = extractor.extract(map, 640, 480).first().quad

        assertThat(quad.isConvex()).isTrue()
        assertThat(quad.topLeft.x).isLessThan(quad.topRight.x)
        assertThat(quad.topLeft.y).isLessThan(quad.bottomLeft.y)
        assertThat(quad.bottomLeft.x).isLessThan(quad.bottomRight.x)
    }

    @Test
    fun `confidence reflects the mean probability inside the candidate`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)), inside = 0.9f)
        val candidate = extractor.extract(map, 640, 480).first()

        assertThat(candidate.confidence).isWithin(0.15f).of(0.9f)
    }

    @Test
    fun `area is reported in frame space`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))
        val candidate = extractor.extract(map, 640, 480).first()

        // 31x31 model pixels scaled by 10 and 7.5 -> ~310 x 232.5
        assertThat(candidate.area).isGreaterThan(60_000f)
        assertThat(candidate.area).isLessThan(85_000f)
    }

    // ------------------------------------------------------------ ranking & limits

    @Test
    fun `multiple components are ranked by area descending`() {
        val map = rectMap(
            100, 100,
            listOf(
                intArrayOf(5, 5, 20, 20),      // 16x16 = 256
                intArrayOf(40, 40, 89, 89)     // 50x50 = 2500
            )
        )

        val candidates = extractor.extract(map, 1000, 1000)

        assertThat(candidates).hasSize(2)
        assertThat(candidates[0].area).isGreaterThan(candidates[1].area)
    }

    @Test
    fun `maxCandidates caps the returned count`() {
        val rects = (0 until 8).map { i ->
            val x = (i % 4) * 20
            val y = (i / 4) * 20
            intArrayOf(x + 2, y + 2, x + 15, y + 15)
        }
        val map = rectMap(100, 60, rects)
        val capped = DefaultCandidateExtractor(maxCandidates = 3)

        assertThat(capped.extract(map, 500, 500)).hasSize(3)
    }

    @Test
    fun `minAreaFraction filters components too small for the frame`() {
        val map = rectMap(
            100, 100,
            listOf(
                intArrayOf(50, 50, 51, 51),    // 2x2 = 4 px
                intArrayOf(10, 10, 49, 49)     // 40x40 = 1600 px
            )
        )

        // 1% of 100x100 = 100 px, so the 4-pixel speck must be dropped.
        val candidates = extractor.extract(map, 500, 500)

        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].area).isGreaterThan(0f)
    }

    @Test
    fun `threshold parameter controls what counts as foreground`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)), inside = 0.6f)

        // Default threshold 0.5 keeps it.
        assertThat(extractor.extract(map, 640, 480)).hasSize(1)
        // A threshold above the block's probability rejects it.
        assertThat(DefaultCandidateExtractor(threshold = 0.8f).extract(map, 640, 480)).isEmpty()
    }

    @Test
    fun `candidate ids are deterministic across runs`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))

        val first = extractor.extract(map, 640, 480).map { it.id }
        val second = extractor.extract(map, 640, 480).map { it.id }

        assertThat(first).isEqualTo(second)
        assertThat(first).isNotEmpty()
    }

    // ------------------------------------------------------------ degenerate input

    @Test
    fun `an all-background map yields no candidates`() {
        val map = ProbabilityMap(32, 32, FloatArray(32 * 32) { 0.01f })
        assertThat(extractor.extract(map, 640, 480)).isEmpty()
    }

    @Test
    fun `an all-foreground map yields a full-frame candidate`() {
        val map = ProbabilityMap(32, 32, FloatArray(32 * 32) { 1f })
        val candidates = extractor.extract(map, 640, 480)

        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].quad.isConvex()).isTrue()
    }

    @Test
    fun `invalid frame dimensions yield no candidates`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))

        assertThat(extractor.extract(map, frameWidth = 0, frameHeight = 480)).isEmpty()
        assertThat(extractor.extract(map, frameWidth = 640, frameHeight = -1)).isEmpty()
    }

    @Test
    fun `constructor rejects out of range parameters`() {
        val bad = listOf<() -> Unit>(
            { DefaultCandidateExtractor(threshold = 1.5f) },
            { DefaultCandidateExtractor(threshold = -0.1f) },
            { DefaultCandidateExtractor(minAreaFraction = 2f) },
            { DefaultCandidateExtractor(maxCandidates = 0) }
        )
        for (build in bad) {
            val result = runCatching { build() }
            assertThat(result.isFailure).isTrue()
        }
    }

    @Test
    fun `pipeline is stable under repeated invocation`() {
        val map = rectMap(64, 64, listOf(intArrayOf(16, 16, 47, 47)))
        val first = extractor.extract(map, 640, 480).first()

        repeat(5) {
            val again = extractor.extract(map, 640, 480).first()
            assertThat(again.area).isWithin(0.001f).of(first.area)
            assertThat(again.confidence).isWithin(0.0001f).of(first.confidence)
        }
    }

    @Test
    fun `a diagonal band is not mistaken for a document quad`() {
        // A thin diagonal strip: the fitter's area-ratio gate should reject it.
        val width = 80
        val height = 80
        val values = FloatArray(width * height) { 0.02f }
        for (i in 0 until 80) {
            for (t in -1..1) {
                val x = i
                val y = i + t
                if (x in 0 until width && y in 0 until height) values[y * width + x] = 0.95f
            }
        }
        val map = ProbabilityMap(width, height, values)

        val candidates = extractor.extract(map, 800, 800)

        // Either nothing, or at most a candidate that does not claim a large area.
        for (c in candidates) {
            assertThat(c.area).isLessThan(800f * 800f * 0.2f)
        }
    }
}
