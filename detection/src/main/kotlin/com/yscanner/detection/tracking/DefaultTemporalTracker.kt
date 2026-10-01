package com.yscanner.detection.tracking

import com.yscanner.detection.select.TrackedTarget
import com.yscanner.detection.select.TrackingState
import com.yscanner.domain.model.Quad

/**
 * Default [TemporalTracker]: dampens detector jitter on the selected quad and keeps a lost target
 * on screen for a short grace period.
 *
 * This class owns **geometry only**. It never re-derives identity or stability — [TrackingState]
 * is passed through from the selector untouched. See [TemporalTracker] for why that split exists;
 * briefly, two components independently deciding "is this stable?" would eventually disagree and
 * the overlay would contradict itself.
 *
 * ### Identity change forces a cold start
 *
 * [TrackedTarget.id] is stable while the same document is tracked and changes on a deliberate
 * switch (a tap, or the selector adopting a different document). When the id changes, the smoother
 * is [QuadSmoother.reset] before the new quad is fed in. This is the single most important
 * behaviour in this file: without it the smoother would low-pass the new document's corners
 * *together with the old document's history*, and the overlay would visibly slide from one
 * document to the other over the next several frames. A new id means a new document, and a new
 * document has no history here — so the first frame of a new identity is emitted essentially
 * verbatim.
 *
 * ### Loss behaviour reuses the retained quad, it does not re-filter it
 *
 * While the input is `null` the tracker re-emits the **last smoothed quad verbatim** (not fed back
 * through the filter) with [TrackingState.LOST]. Re-filtering a constant value would only decay it
 * toward itself and, worse, advance the filter's timestamps so the first frame after the gap would
 * be differenced against a stale sample — adding fake lag exactly when the target reappears. The
 * retained quad is a frozen snapshot, so it costs nothing and stays put.
 *
 * Once the gap reaches [lossToleranceFrames] the target is written off: the retained quad is
 * dropped and the smoother is reset, and every later `null` returns `null`.
 *
 * Determinism: no clock is read; timestamps come from the caller. The same input sequence always
 * produces the same output.
 *
 * @param smoother the geometry filter applied to each present target. Shared across identities but
 *   reset on every identity change, so history never crosses documents.
 * @param lossToleranceFrames consecutive absent frames tolerated before the target is dropped for
 *   good. Must be at least 1; with 1, the first absent frame already returns `null`.
 */
class DefaultTemporalTracker(
    private val smoother: QuadSmoother = QuadSmoother(),
    /** Consecutive frames the target may be absent before it is dropped entirely. */
    private val lossToleranceFrames: Int = 5
) : TemporalTracker {

    init {
        require(lossToleranceFrames >= 1) {
            "lossToleranceFrames must be at least 1 but was $lossToleranceFrames"
        }
    }

    /**
     * Last smoothed quad, retained so the overlay can keep drawing through a brief loss. `null`
     * until the first present target, and cleared again once the loss tolerance is exceeded.
     */
    private var retainedQuad: Quad? = null

    /** Confidence that accompanied [retainedQuad], echoed while the target is lost. */
    private var retainedConfidence: Float = 0f

    /** Consecutive absent frames since the last present target. */
    private var missedFrames = 0

    /**
     * Identity of the last present target. Kept so a change can reset [smoother]; a reappearance
     * with the *same* id must resume rather than restart, which is what makes a brief loss cheap.
     */
    private var lastId: String? = null

    override fun update(target: TrackedTarget?, timestampSeconds: Double): SmoothedTarget? {
        if (target == null) return updateAbsent()

        // A different id is a different document. Wipe the smoother so the new document's corners
        // are not blended with the old document's history.
        if (target.id != lastId) {
            smoother.reset()
        }
        lastId = target.id
        missedFrames = 0

        val smoothed = smoother.smooth(target.quad, timestampSeconds)
        retainedQuad = smoothed
        retainedConfidence = target.confidence

        return SmoothedTarget(
            quad = smoothed,
            confidence = target.confidence,
            // Identity and stability belong to the selector. Pass them through untouched.
            trackingState = target.trackingState,
            stableFrameCount = target.stableFrameCount,
            isReadyForCapture = target.trackingState == TrackingState.STABLE
        )
    }

    /**
     * Handles a frame with no target: keep showing the retained quad for a while, then give up.
     *
     * @return the retained quad as [TrackingState.LOST], or `null` once there is nothing to show.
     */
    private fun updateAbsent(): SmoothedTarget? {
        // Nothing has ever been smoothed, so there is nothing to fade out.
        val retained = retainedQuad ?: return null

        missedFrames++
        if (missedFrames >= lossToleranceFrames) {
            // The gap is longer than we tolerate: the target is gone for good.
            retainedQuad = null
            retainedConfidence = 0f
            smoother.reset()
            return null
        }

        // Re-emit the frozen snapshot. Do NOT feed it back through the smoother.
        return SmoothedTarget(
            quad = retained,
            confidence = retainedConfidence,
            trackingState = TrackingState.LOST,
            stableFrameCount = 0,
            isReadyForCapture = false
        )
    }

    override fun reset() {
        retainedQuad = null
        retainedConfidence = 0f
        missedFrames = 0
        lastId = null
        smoother.reset()
    }
}
