package com.yscanner.detection.tracking

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.PointF
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

/**
 * Behavioural tests for [OneEuroFilter] and [PointFOneEuroFilter].
 *
 * All data is deterministic (fixed seeds, caller-supplied timestamps) so these
 * tests can never flake on timing.
 */
class OneEuroFilterTest {

    /** 30 fps, the rate the detection loop runs at on the target devices. */
    private val dt = 1.0 / 30.0

    @Test
    fun `constant input converges to the constant`() {
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        var output = 0.0
        var t = 0.0
        repeat(200) {
            output = filter.filter(42.0, t)
            t += dt
        }
        assertThat(output).isWithin(1e-6).of(42.0)
    }

    @Test
    fun `step input approaches monotonically without overshoot`() {
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        var t = 0.0
        repeat(20) {
            filter.filter(0.0, t)
            t += dt
        }

        val outputs = ArrayList<Double>(200)
        repeat(200) {
            outputs += filter.filter(1.0, t)
            t += dt
        }

        // Monotonically non-decreasing ...
        for (i in 1 until outputs.size) {
            assertThat(outputs[i] >= outputs[i - 1] - 1e-12).isTrue()
        }
        // ... and never overshooting the step value.
        for (v in outputs) {
            assertThat(v <= 1.0 + 1e-9).isTrue()
        }
        assertThat(outputs.last()).isWithin(1e-3).of(1.0)
    }

    @Test
    fun `lower min cutoff yields a smoother output on a noisy signal`() {
        val random = Random(42)
        val signal = DoubleArray(400) { 100.0 + random.nextDouble(-5.0, 5.0) }

        val smoothFilter = OneEuroFilter(minCutoff = 0.3, beta = 0.0)
        val responsiveFilter = OneEuroFilter(minCutoff = 5.0, beta = 0.0)

        val smoothOut = ArrayList<Double>(signal.size)
        val responsiveOut = ArrayList<Double>(signal.size)
        var t = 0.0
        for (v in signal) {
            smoothOut += smoothFilter.filter(v, t)
            responsiveOut += responsiveFilter.filter(v, t)
            t += dt
        }

        // Discard the warm-up transient before measuring variance.
        val smoothVariance = variance(smoothOut.drop(50))
        val responsiveVariance = variance(responsiveOut.drop(50))

        assertThat(smoothVariance).isLessThan(responsiveVariance)
    }

    @Test
    fun `higher beta reduces lag on a fast ramp`() {
        val adaptive = OneEuroFilter(minCutoff = 0.5, beta = 1.0)
        val laggy = OneEuroFilter(minCutoff = 0.5, beta = 0.0)

        var t = 0.0
        var adaptiveError = 0.0
        var laggyError = 0.0
        var count = 0
        for (i in 0 until 200) {
            val target = 100.0 + 5.0 * i
            val a = adaptive.filter(target, t)
            val b = laggy.filter(target, t)
            if (i > 20) {
                adaptiveError += abs(a - target)
                laggyError += abs(b - target)
                count++
            }
            t += dt
        }

        assertThat(adaptiveError / count).isLessThan(laggyError / count)
    }

    @Test
    fun `non positive dt never produces NaN or Infinity`() {
        val filter = OneEuroFilter()
        filter.filter(1.0, 0.0)

        // Repeated identical timestamps.
        repeat(10) {
            val v = filter.filter(2.0, 0.0)
            assertThat(v.isFinite()).isTrue()
        }
        // Backwards time.
        repeat(10) {
            val v = filter.filter(3.0, -1.0)
            assertThat(v.isFinite()).isTrue()
        }
        // Non-finite timestamp and non-finite value are both rejected safely.
        assertThat(filter.filter(4.0, Double.NaN).isFinite()).isTrue()
        assertThat(filter.filter(Double.POSITIVE_INFINITY, dt).isFinite()).isTrue()
    }

    @Test
    fun `reset restores the initial condition`() {
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        var t = 0.0
        repeat(50) {
            filter.filter(10.0, t)
            t += dt
        }

        filter.reset()

        val afterReset = filter.filter(7.0, 0.0)
        val fresh = OneEuroFilter(minCutoff = 1.0, beta = 0.0).filter(7.0, 0.0)

        assertThat(afterReset).isEqualTo(7.0)
        assertThat(afterReset).isEqualTo(fresh)
    }

    @Test
    fun `rejects non positive cutoffs`() {
        val threw = try {
            OneEuroFilter(minCutoff = 0.0, beta = 0.0)
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertThat(threw).isTrue()
    }

    @Test
    fun `point filter smooths x and y independently and correctly`() {
        val pointFilter = PointFOneEuroFilter(minCutoff = 1.0, beta = 0.0)
        val xFilter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        val yFilter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)

        val random = Random(42)
        var t = 0.0
        repeat(120) {
            val x = 50f + random.nextDouble(-3.0, 3.0).toFloat()
            val y = 200f + random.nextDouble(-3.0, 3.0).toFloat()

            val expected = PointF(
                xFilter.filter(x.toDouble(), t).toFloat(),
                yFilter.filter(y.toDouble(), t).toFloat()
            )
            val actual = pointFilter.filter(PointF(x, y), t)

            assertThat(actual.x).isWithin(1e-6f).of(expected.x)
            assertThat(actual.y).isWithin(1e-6f).of(expected.y)
            t += dt
        }
    }

    @Test
    fun `point filter reset clears both axes`() {
        val pointFilter = PointFOneEuroFilter(minCutoff = 1.0, beta = 0.0)
        var t = 0.0
        repeat(30) {
            pointFilter.filter(PointF(100f, 100f), t)
            t += dt
        }

        pointFilter.reset()
        val out = pointFilter.filter(PointF(5f, 9f), 0.0)

        assertThat(out.x).isEqualTo(5f)
        assertThat(out.y).isEqualTo(9f)
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.sum() / values.size
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }
}
