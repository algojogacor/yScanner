package com.yscanner.detection.tracking

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.DocumentCandidate
import com.yscanner.detection.select.DefaultTargetSelector
import com.yscanner.detection.select.TargetSelectorConfig
import com.yscanner.detection.select.TrackedTarget
import com.yscanner.detection.select.TrackingState
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.Quad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Behavioural tests for [DefaultTemporalTracker].
 *
 * Every sequence uses a fixed seed and caller-supplied timestamps, so the tracker's
 * output is deterministic and the assertions can be exact where the spec calls for it.
 */
class DefaultTemporalTrackerTest {

    private val dt = 1.0 / 30.0

    @Test
    fun `passes the selector's tracking state through unchanged`() {
        for (state in listOf(TrackingState.STABLE, TrackingState.ACQUIRING, TrackingState.TRACKING)) {
            val tracker = DefaultTemporalTracker()
            val out = tracker.update(target("doc", rect(), state = state), 0.0)

            assertThat(out).isNotNull()
            assertThat(out!!.trackingState).isEqualTo(state)
        }
    }

    @Test
    fun `returns null when there is nothing to show`() {
        val tracker = DefaultTemporalTracker()

        assertThat(tracker.update(null, 0.0)).isNull()
    }

    @Test
    fun `smooths a jittery sequence so consecutive frames move less`() {
        val random = Random(42)
        val base = rect()
        val raw = ArrayList<Quad>(200)
        repeat(200) { raw += jitter(base, random, 2f) }

        val tracker = DefaultTemporalTracker(
            QuadSmoother(SmootherConfig(minCutoff = 0.5, beta = 0.0), SmoothingMethod.ONE_EURO)
        )

        val smoothed = ArrayList<Quad>(raw.size)
        var t = 0.0
        for (q in raw) {
            smoothed += tracker.update(target("doc", q), t)!!.quad
            t += dt
        }

        val rawStats = JitterStats.of(raw, warmupFrames = 10)
        val smoothedStats = JitterStats.of(smoothed, warmupFrames = 10)

        assertThat(smoothedStats.meanCornerDisplacement)
            .isLessThan(rawStats.meanCornerDisplacement)
    }

    @Test
    fun `retains the last quad as LOST while the target is absent`() {
        val tracker = DefaultTemporalTracker(lossToleranceFrames = 5)
        val lastSmoothed = tracker.update(target("doc", rect()), 0.0)!!.quad

        var t = dt
        repeat(3) {
            val out = tracker.update(null, t)
            assertThat(out).isNotNull()
            assertThat(out!!.trackingState).isEqualTo(TrackingState.LOST)
            assertThat(out.quad).isEqualTo(lastSmoothed)
            assertThat(out.stableFrameCount).isEqualTo(0)
            assertThat(out.isReadyForCapture).isFalse()
            t += dt
        }
    }

    @Test
    fun `drops to null once the loss tolerance is exceeded`() {
        val tracker = DefaultTemporalTracker(lossToleranceFrames = 3)
        tracker.update(target("doc", rect()), 0.0)

        assertThat(tracker.update(null, dt)).isNotNull()
        assertThat(tracker.update(null, 2 * dt)).isNotNull()
        assertThat(tracker.update(null, 3 * dt)).isNull()
        assertThat(tracker.update(null, 4 * dt)).isNull()
    }

    @Test
    fun `resumes smoothing after a brief loss`() {
        val tracker = DefaultTemporalTracker(
            QuadSmoother(SmootherConfig(minCutoff = 1.0, beta = 1.0), SmoothingMethod.ONE_EURO),
            lossToleranceFrames = 5
        )
        val from = rect(cx = 50f, cy = 50f)
        val to = rect(cx = 150f, cy = 150f)

        tracker.update(target("doc", from), 0.0)
        assertThat(tracker.update(null, dt)).isNotNull()
        assertThat(tracker.update(null, 2 * dt)).isNotNull()

        val resumed = tracker.update(target("doc", to), 3 * dt)!!.quad

        // The filter must have followed the document, not stayed frozen on the retained quad.
        assertThat(meanCornerDistance(resumed, to)).isLessThan(meanCornerDistance(resumed, from))
        assertThat(meanCornerDistance(resumed, to)).isLessThan(5.0)
    }

    @Test
    fun `resets the smoother when the target identity changes`() {
        // A strongly smoothing filter: if history leaked across identities the first frame of the
        // new document would be an ~90/10 blend leaning hard toward the old document.
        val tracker = DefaultTemporalTracker(
            QuadSmoother(SmootherConfig(minCutoff = 0.5, beta = 0.0), SmoothingMethod.ONE_EURO)
        )
        val docA = rect(cx = 50f, cy = 50f)
        val docB = rect(cx = 200f, cy = 200f)

        var t = 0.0
        repeat(30) {
            tracker.update(target("A", docA), t)
            t += dt
        }

        val firstForB = tracker.update(target("B", docB), t)!!.quad

        // A new identity starts cold: the first output is the new quad itself, not a blend.
        assertQuadClose(firstForB, docB, 1e-3f)
        assertThat(meanCornerDistance(firstForB, docB))
            .isLessThan(meanCornerDistance(firstForB, docA))
    }

    @Test
    fun `reset clears retained state`() {
        val tracker = DefaultTemporalTracker()
        tracker.update(target("doc", rect()), 0.0)

        tracker.reset()

        assertThat(tracker.update(null, dt)).isNull()
    }

    @Test
    fun `isReadyForCapture is true only when stable`() {
        val states = listOf(
            TrackingState.STABLE to true,
            TrackingState.ACQUIRING to false,
            TrackingState.TRACKING to false
        )
        for ((state, expected) in states) {
            val tracker = DefaultTemporalTracker()
            val out = tracker.update(target("doc", rect(), state = state), 0.0)
            assertThat(out!!.isReadyForCapture).isEqualTo(expected)
        }

        // A lost target is never ready to capture.
        val tracker = DefaultTemporalTracker()
        tracker.update(target("doc", rect(), state = TrackingState.STABLE), 0.0)
        assertThat(tracker.update(null, dt)!!.isReadyForCapture).isFalse()
    }

    @Test
    fun `rejects a non positive loss tolerance`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefaultTemporalTracker(lossToleranceFrames = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DefaultTemporalTracker(lossToleranceFrames = -1)
        }
    }

    @Test
    fun `overlay stops being drawn once the document leaves for good`() {
        // Integration check across the two components, because each is correct alone but the
        // composition is where a fade-out can fail to terminate. The selector keeps reporting a
        // target for a few frames after the document disappears; the tracker keeps drawing it for
        // a few more. After that the outline MUST leave the screen. If the selector went on
        // claiming a target forever, the tracker would go on drawing forever and the outline would
        // be stuck on the preview.
        val selector = DefaultTargetSelector(TargetSelectorConfig(lostFrameThreshold = 2))
        val tracker = DefaultTemporalTracker(lossToleranceFrames = 3)
        val document = rect()

        var t = 0.0
        var selected = selector.select(listOf(candidate(document)), null, null, 800, 800)
        assertThat(tracker.update(selected, t)).isNotNull()

        // The document is gone. Feed far more frames than either grace period allows.
        var drawn = tracker.update(selected, t)
        repeat(30) {
            t += dt
            selected = selector.select(emptyList(), selected, null, 800, 800)
            drawn = tracker.update(selected, t)
        }

        assertThat(drawn).isNull()
    }

    // ---- helpers -----------------------------------------------------------

    private fun candidate(
        quad: Quad,
        id: String = "candidate",
        confidence: Float = 0.9f
    ): DocumentCandidate = DocumentCandidate(id, quad, confidence, quad.area())

    private fun target(
        id: String,
        quad: Quad,
        state: TrackingState = TrackingState.TRACKING,
        confidence: Float = 0.9f,
        stableFrameCount: Int = 1
    ): TrackedTarget = TrackedTarget(id, quad, confidence, state, stableFrameCount)

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

    private fun meanCornerDistance(a: Quad, b: Quad): Double {
        val pa = a.toArray()
        val pb = b.toArray()
        var sum = 0.0
        var i = 0
        while (i < pa.size) {
            sum += hypot((pa[i] - pb[i]).toDouble(), (pa[i + 1] - pb[i + 1]).toDouble())
            i += 2
        }
        return sum / 4.0
    }

    private fun assertQuadClose(actual: Quad, expected: Quad, epsilon: Float) {
        val a = actual.toArray()
        val b = expected.toArray()
        for (i in a.indices) {
            assertThat(abs(a[i] - b[i]) <= epsilon).isTrue()
        }
    }
}
