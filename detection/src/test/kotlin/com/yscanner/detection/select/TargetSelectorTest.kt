package com.yscanner.detection.select

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.DocumentCandidate
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Test

/**
 * Tests for [DefaultTargetSelector].
 *
 * These run on the JVM with hand-built candidates — no model, no camera, no clock — because
 * the whole point of splitting selection out of detection is that the decision can be pinned
 * down exactly like this.
 */
class TargetSelectorTest {

    private fun quad(x0: Float, y0: Float, x1: Float, y1: Float): Quad = Quad(
        topLeft = Corner(x0, y0),
        topRight = Corner(x1, y0),
        bottomRight = Corner(x1, y1),
        bottomLeft = Corner(x0, y1)
    )

    /** The same geometry expressed in the reverse corner order (negative shoelace winding). */
    private fun reversed(q: Quad): Quad = Quad(
        topLeft = q.topLeft,
        topRight = q.bottomLeft,
        bottomRight = q.bottomRight,
        bottomLeft = q.topRight
    )

    private fun candidate(id: String, q: Quad, confidence: Float = 0.5f): DocumentCandidate =
        DocumentCandidate(id = id, quad = q, confidence = confidence, area = q.area())

    private fun tracked(id: String, q: Quad, confidence: Float = 0.5f): TrackedTarget =
        TrackedTarget(id, q, confidence, TrackingState.TRACKING, 2)

    private val left = quad(50f, 300f, 250f, 500f)
    private val right = quad(550f, 300f, 750f, 500f)

    // ---------------------------------------------------------------- tap override

    @Test
    fun `selects candidate containing the tap point`() {
        val selector = DefaultTargetSelector()
        val candidates = listOf(candidate("left", left), candidate("right", right))
        val previous = tracked("target-old", left)

        // Tap well inside the right document.
        val result = selector.select(candidates, previous, PointF(650f, 400f), 800, 800)

        assertThat(result).isNotNull()
        assertThat(result!!.quad).isEqualTo(right)
        assertThat(result.id).isNotEqualTo(previous.id)
        assertThat(result.trackingState).isEqualTo(TrackingState.TRACKING)
        assertThat(result.stableFrameCount).isEqualTo(0)
    }

    @Test
    fun `tap outside any candidate falls back to heuristics`() {
        val selector = DefaultTargetSelector()
        val candidates = listOf(candidate("left", left), candidate("right", right))
        val previous = tracked("target-old", left)

        // Empty space between the two documents.
        val result = selector.select(candidates, previous, PointF(400f, 400f), 800, 800)

        assertThat(result).isNotNull()
        // The tap selects nothing, so temporal persistence keeps the previous identity.
        assertThat(result!!.id).isEqualTo(previous.id)
        assertThat(result.quad).isEqualTo(left)
    }

    // ---------------------------------------------------------------- persistence

    @Test
    fun `maintains target persistence across frames`() {
        val selector = DefaultTargetSelector()
        val first = listOf(candidate("left", left), candidate("right", right))

        var previous = selector.select(first, null, null, 800, 800)
        assertThat(previous).isNotNull()
        val lockedId = previous!!.id

        repeat(4) { frame ->
            // The other document's confidence is raised above the locked one — persistence
            // must still win, otherwise the overlay would flicker between the two.
            val candidates = listOf(
                candidate("left", left, confidence = 0.5f),
                candidate("right", right, confidence = 0.99f)
            )
            previous = selector.select(candidates, previous, null, 800, 800)
            assertThat(previous!!.id).isEqualTo(lockedId)
        }
    }

    @Test
    fun `holds the previous identity when a visible candidate stops matching`() {
        val selector = DefaultTargetSelector(TargetSelectorConfig(lostFrameThreshold = 3))
        val candidates = listOf(candidate("left", left))

        val acquired = selector.select(candidates, null, null, 800, 800)
        val matched = selector.select(candidates, acquired, null, 800, 800)
        assertThat(matched!!.trackingState).isEqualTo(TrackingState.TRACKING)

        // A different document is all the detector can see now. The lock must survive a few
        // frames of this: adopting the visible candidate straight away would hand the target to
        // the other document permanently, which is exactly the flicker this component exists to
        // prevent.
        val other = listOf(candidate("right", right))
        val hold1 = selector.select(other, matched, null, 800, 800)
        assertThat(hold1!!.id).isEqualTo(matched.id)
        assertThat(hold1.quad).isEqualTo(left)
        assertThat(hold1.trackingState).isEqualTo(TrackingState.ACQUIRING)

        val hold2 = selector.select(other, hold1, null, 800, 800)
        assertThat(hold2!!.id).isEqualTo(matched.id)

        // Past the threshold the old target is given up and the visible document is adopted.
        val adopted = selector.select(other, hold2, null, 800, 800)
        assertThat(adopted!!.id).isNotEqualTo(matched.id)
        assertThat(adopted.quad).isEqualTo(right)
        assertThat(adopted.trackingState).isEqualTo(TrackingState.ACQUIRING)
    }

    @Test
    fun `a briefly lost target resumes its identity when it comes back`() {
        val selector = DefaultTargetSelector(TargetSelectorConfig(lostFrameThreshold = 5))
        val candidates = listOf(candidate("left", left))

        val acquired = selector.select(candidates, null, null, 800, 800)
        val matched = selector.select(candidates, acquired, null, 800, 800)
        assertThat(matched!!.trackingState).isEqualTo(TrackingState.TRACKING)

        // Two frames with nothing detected, then the document is back.
        val miss1 = selector.select(emptyList(), matched, null, 800, 800)
        val miss2 = selector.select(emptyList(), miss1, null, 800, 800)
        assertThat(miss2!!.trackingState).isEqualTo(TrackingState.ACQUIRING)

        // Identity survived the gap, so this is the same document, not a new acquisition.
        val recovered = selector.select(candidates, miss2, null, 800, 800)
        assertThat(recovered!!.id).isEqualTo(matched.id)
        assertThat(recovered.trackingState).isEqualTo(TrackingState.TRACKING)
    }

    @Test
    fun `winding order of a quad does not affect matching`() {
        val selector = DefaultTargetSelector()
        val previous = selector.select(listOf(candidate("a", left)), null, null, 800, 800)
        assertThat(previous).isNotNull()

        // Same geometry, reversed corner order: IoU must still be ~1, so identity survives.
        val reversedCandidate = candidate("a", reversed(left))
        val result = selector.select(listOf(reversedCandidate), previous, null, 800, 800)

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(previous!!.id)
        assertThat(result.trackingState).isEqualTo(TrackingState.TRACKING)
    }

    // ---------------------------------------------------------------- loss & stability

    @Test
    fun `transitions to lost after N frames without a match`() {
        val selector = DefaultTargetSelector(TargetSelectorConfig(lostFrameThreshold = 3))
        val candidates = listOf(candidate("left", left))

        val acquired = selector.select(candidates, null, null, 800, 800)
        assertThat(acquired!!.trackingState).isEqualTo(TrackingState.ACQUIRING)

        val matched = selector.select(candidates, acquired, null, 800, 800)
        assertThat(matched!!.trackingState).isEqualTo(TrackingState.TRACKING)

        // Frames 1 and 2 without a candidate: not confirmed, but not yet lost.
        val miss1 = selector.select(emptyList(), matched, null, 800, 800)
        assertThat(miss1!!.trackingState).isEqualTo(TrackingState.ACQUIRING)
        val miss2 = selector.select(emptyList(), miss1, null, 800, 800)
        assertThat(miss2!!.trackingState).isEqualTo(TrackingState.ACQUIRING)

        // Frame 3 reaches the threshold.
        val miss3 = selector.select(emptyList(), miss2, null, 800, 800)
        assertThat(miss3!!.trackingState).isEqualTo(TrackingState.LOST)
        assertThat(miss3.stableFrameCount).isEqualTo(0)
    }

    @Test
    fun `promotes to stable after the stable frame threshold`() {
        val selector = DefaultTargetSelector(TargetSelectorConfig(stableFrameThreshold = 3))
        val candidates = listOf(candidate("left", left))

        val frame1 = selector.select(candidates, null, null, 800, 800)
        assertThat(frame1!!.trackingState).isEqualTo(TrackingState.ACQUIRING)
        assertThat(frame1.stableFrameCount).isEqualTo(1)

        // First temporal match: continuous identity, not yet stable.
        val frame2 = selector.select(candidates, frame1, null, 800, 800)
        assertThat(frame2!!.trackingState).isEqualTo(TrackingState.TRACKING)
        assertThat(frame2.stableFrameCount).isEqualTo(2)

        // Third consecutive frame with the target reaches the threshold.
        val frame3 = selector.select(candidates, frame2, null, 800, 800)
        assertThat(frame3!!.trackingState).isEqualTo(TrackingState.STABLE)
        assertThat(frame3.stableFrameCount).isEqualTo(3)
    }

    // ---------------------------------------------------------------- cold-start heuristic

    @Test
    fun `prioritizes central largest candidate when there is no history`() {
        val selector = DefaultTargetSelector()

        // Small centred document vs a larger one tucked into a corner: centrality must win.
        val smallCentred = quad(350f, 350f, 450f, 450f)
        val largeCorner = quad(0f, 0f, 300f, 300f)
        val central = selector.select(
            listOf(candidate("corner", largeCorner), candidate("centre", smallCentred)),
            null, null, 800, 800
        )
        assertThat(central!!.quad).isEqualTo(smallCentred)

        // Both centred: the larger one must win.
        val selector2 = DefaultTargetSelector()
        val largeCentred = quad(300f, 300f, 500f, 500f)
        val tinyCentred = quad(380f, 380f, 420f, 420f)
        val largest = selector2.select(
            listOf(candidate("tiny", tinyCentred), candidate("large", largeCentred)),
            null, null, 800, 800
        )
        assertThat(largest!!.quad).isEqualTo(largeCentred)
        assertThat(largest.trackingState).isEqualTo(TrackingState.ACQUIRING)
    }

    // ---------------------------------------------------------------- degenerate & reset

    @Test
    fun `returns null when there are no candidates and no previous target`() {
        val selector = DefaultTargetSelector()
        assertThat(selector.select(emptyList(), null, null, 800, 800)).isNull()
    }

    @Test
    fun `reset clears tracking state`() {
        val selector = DefaultTargetSelector(TargetSelectorConfig(lostFrameThreshold = 2))
        val candidates = listOf(candidate("left", left))

        val acquired = selector.select(candidates, null, null, 800, 800)
        assertThat(acquired!!.id).isEqualTo("target-1")

        // Drive it to LOST, which advances the internal missed-frame counter past the limit.
        val miss1 = selector.select(emptyList(), acquired, null, 800, 800)
        val lost = selector.select(emptyList(), miss1, null, 800, 800)
        assertThat(lost!!.trackingState).isEqualTo(TrackingState.LOST)

        selector.reset()

        // If missedFrames had survived reset, this first miss would immediately re-report LOST.
        val afterReset = selector.select(emptyList(), lost, null, 800, 800)
        assertThat(afterReset!!.trackingState).isEqualTo(TrackingState.ACQUIRING)

        // The id counter is cleared too: the next cold start is target-1 again.
        val coldStart = selector.select(candidates, null, null, 800, 800)
        assertThat(coldStart!!.id).isEqualTo("target-1")
        assertThat(coldStart.trackingState).isEqualTo(TrackingState.ACQUIRING)
    }

    @Test
    fun `invalid frame dimensions do not produce NaN scores`() {
        val selector = DefaultTargetSelector()
        val result = selector.select(listOf(candidate("left", left)), null, null, 0, 0)

        assertThat(result).isNotNull()
        assertThat(result!!.quad).isEqualTo(left)
    }
}
