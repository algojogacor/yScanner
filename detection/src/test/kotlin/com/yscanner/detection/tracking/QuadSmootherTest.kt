package com.yscanner.detection.tracking

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.Quad
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

/**
 * Behavioural tests for [QuadSmoother] and [JitterStats].
 *
 * Every sequence uses a fixed seed and caller-supplied timestamps, so the
 * measurements are deterministic.
 */
class QuadSmootherTest {

    private val dt = 1.0 / 30.0

    @Test
    fun `smoothing reduces per corner jitter versus the raw sequence`() {
        val random = Random(42)
        val base = rect()
        val raw = ArrayList<Quad>(200)
        repeat(200) { raw += jitter(base, random, 2f) }

        val smoother = QuadSmoother(
            SmootherConfig(minCutoff = 0.5, beta = 0.0, exponentialAlpha = 0.3),
            SmoothingMethod.ONE_EURO
        )
        val smoothed = ArrayList<Quad>(raw.size)
        var t = 0.0
        for (q in raw) {
            smoothed += smoother.smooth(q, t)
            t += dt
        }

        val rawStats = JitterStats.of(raw, warmupFrames = 10)
        val smoothedStats = JitterStats.of(smoothed, warmupFrames = 10)

        assertThat(smoothedStats.meanCornerDisplacement)
            .isLessThan(rawStats.meanCornerDisplacement)
    }

    @Test
    fun `stationary quad is returned essentially unchanged after warm up`() {
        val smoother = QuadSmoother(SmootherConfig(), SmoothingMethod.ONE_EURO)
        val quad = rect()

        var t = 0.0
        var out = quad
        repeat(60) {
            out = smoother.smooth(quad, t)
            t += dt
        }

        assertQuadClose(out, quad, 1e-3f)
    }

    @Test
    fun `non convex smoothed result falls back to the previous quad`() {
        val smoother = QuadSmoother(
            SmootherConfig(exponentialAlpha = 0.5),
            SmoothingMethod.EXPONENTIAL
        )

        // Two independently convex detections whose 50/50 blend is reflex: the
        // top-left corner of the blend sits inside the triangle of the other three.
        val previous = Quad(
            Corner(50f, 100f),
            Corner(80f, 70f),
            Corner(80f, 30f),
            Corner(10f, 0f)
        )
        val current = Quad(
            Corner(10f, 20f),
            Corner(20f, 20f),
            Corner(80f, 30f),
            Corner(40f, 50f)
        )
        assertThat(previous.isConvex()).isTrue()
        assertThat(current.isConvex()).isTrue()

        val first = smoother.smooth(previous, 0.0)
        assertThat(first).isEqualTo(previous)

        // The blend would be non-convex, so the smoother emits the previous quad.
        val out = smoother.smooth(current, dt)
        assertThat(out).isEqualTo(previous)
    }

    @Test
    fun `output is always convex once a convex history exists`() {
        val random = Random(42)
        val smoother = QuadSmoother(
            SmootherConfig(minCutoff = 0.7, beta = 0.01, exponentialAlpha = 0.4),
            SmoothingMethod.ONE_EURO
        )

        var t = 0.0
        smoother.smooth(rect(), t)
        t += dt

        repeat(200) {
            val quad = if (random.nextBoolean()) {
                jitter(rect(), random, 2f)
            } else {
                // A deliberately extreme quad, sometimes non-convex itself.
                Quad(
                    Corner(
                        random.nextDouble(20.0, 80.0).toFloat(),
                        random.nextDouble(20.0, 80.0).toFloat()
                    ),
                    Corner(90f, 10f),
                    Corner(90f, 90f),
                    Corner(10f, 90f)
                )
            }
            val out = smoother.smooth(quad, t)
            assertThat(out.isConvex()).isTrue()
            t += dt
        }
    }

    @Test
    fun `corner confidence is preserved exactly`() {
        val smoother = QuadSmoother(SmootherConfig(), SmoothingMethod.ONE_EURO)
        val quad = Quad(
            Corner(10f, 10f, 0.25f),
            Corner(90f, 10f, 0.5f),
            Corner(90f, 90f, 0.75f),
            Corner(10f, 90f, 1.0f)
        )

        var t = 0.0
        var out = quad
        repeat(30) {
            out = smoother.smooth(quad, t)
            t += dt
        }

        assertThat(out.topLeft.confidence).isEqualTo(0.25f)
        assertThat(out.topRight.confidence).isEqualTo(0.5f)
        assertThat(out.bottomRight.confidence).isEqualTo(0.75f)
        assertThat(out.bottomLeft.confidence).isEqualTo(1.0f)
    }

    @Test
    fun `reset clears history`() {
        val smoother = QuadSmoother(
            SmootherConfig(exponentialAlpha = 0.5),
            SmoothingMethod.EXPONENTIAL
        )
        var t = 0.0
        repeat(20) {
            smoother.smooth(rect(), t)
            t += dt
        }

        smoother.reset()

        val fresh = rect(cx = 100f, cy = 100f)
        val out = smoother.smooth(fresh, 5.0)
        assertThat(out).isEqualTo(fresh)
    }

    @Test
    fun `both methods produce finite convex output on a noisy sequence`() {
        for (method in SmoothingMethod.entries) {
            val random = Random(42)
            val base = rect()
            val smoother = QuadSmoother(
                SmootherConfig(minCutoff = 0.5, beta = 0.02, exponentialAlpha = 0.35),
                method
            )

            var t = 0.0
            repeat(120) {
                val out = smoother.smooth(jitter(base, random, 2f), t)
                assertThat(out.isConvex()).isTrue()
                for (v in out.toArray()) {
                    assertThat(v.isFinite()).isTrue()
                }
                t += dt
            }
        }
    }

    @Test
    fun `compare reports jitter and a positive lag proxy`() {
        val random = Random(42)
        val raw = ArrayList<Quad>(150)
        repeat(150) { raw += jitter(rect(), random, 2f) }

        val smoother = QuadSmoother(
            SmootherConfig(exponentialAlpha = 0.2),
            SmoothingMethod.EXPONENTIAL
        )
        val smoothed = ArrayList<Quad>(raw.size)
        var t = 0.0
        for (q in raw) {
            smoothed += smoother.smooth(q, t)
            t += dt
        }

        val stats = JitterStats.compare(raw, smoothed, warmupFrames = 10)

        assertThat(stats.frames).isEqualTo(150)
        assertThat(stats.meanLag).isGreaterThan(0.0)
        assertThat(stats.meanCornerDisplacement)
            .isLessThan(JitterStats.of(raw, warmupFrames = 10).meanCornerDisplacement)
        // `of` has no raw reference, so it reports no lag.
        assertThat(JitterStats.of(raw).meanLag).isEqualTo(0.0)
    }

    @Test
    fun `empty sequences yield empty stats`() {
        assertThat(JitterStats.of(emptyList())).isEqualTo(JitterStats.EMPTY)
        assertThat(JitterStats.compare(emptyList(), emptyList())).isEqualTo(JitterStats.EMPTY)
    }

    // ---- helpers -----------------------------------------------------------

    private fun rect(cx: Float = 50f, cy: Float = 50f, w: Float = 40f, h: Float = 60f): Quad =
        Quad(
            Corner(cx - w / 2f, cy - h / 2f),
            Corner(cx + w / 2f, cy - h / 2f),
            Corner(cx + w / 2f, cy + h / 2f),
            Corner(cx - w / 2f, cy + h / 2f)
        )

    private fun jitter(base: Quad, random: Random, amount: Float): Quad {
        fun offset(): Float = random.nextDouble(-amount.toDouble(), amount.toDouble()).toFloat()
        fun jitterCorner(c: Corner): Corner = Corner(c.x + offset(), c.y + offset(), c.confidence)
        return Quad(
            jitterCorner(base.topLeft),
            jitterCorner(base.topRight),
            jitterCorner(base.bottomRight),
            jitterCorner(base.bottomLeft)
        )
    }

    private fun assertQuadClose(actual: Quad, expected: Quad, epsilon: Float) {
        val a = actual.toArray()
        val b = expected.toArray()
        for (i in a.indices) {
            assertThat(abs(a[i] - b[i]) <= epsilon).isTrue()
        }
    }
}
