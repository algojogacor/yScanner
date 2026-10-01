package com.yscanner.detection.tracking

import com.yscanner.detection.select.TrackedTarget
import com.yscanner.detection.select.TrackingState
import com.yscanner.domain.model.Quad

/**
 * A tracked target after temporal smoothing, in the same frame space as the input.
 *
 * This is what the overlay consumes. It is deliberately a *display* type: it carries only what a
 * renderer needs, so the UI never has to know about the segmentation model, the candidate list or
 * the camera sensor (ARCHITECTURE.md §17).
 *
 * @param quad the smoothed quadrilateral. Always convex — [QuadSmoother] rejects a non-convex blend
 *   rather than emitting a self-intersecting shape.
 * @param confidence passed through from the detector, never invented by smoothing.
 * @param trackingState the selector's verdict for this frame, passed through unchanged. See
 *   [TemporalTracker] for why stability is not re-derived here.
 * @param stableFrameCount consecutive frames this identity has been matched.
 * @param isReadyForCapture **provisional** gate for auto-capture. Currently true only in
 *   [TrackingState.STABLE]. M07 owns the real readiness decision and is expected to tighten this
 *   with quality and coverage checks once M10 exists — treat this flag as a hint, not as a contract.
 */
data class SmoothedTarget(
    val quad: Quad,
    val confidence: Float,
    val trackingState: TrackingState,
    val stableFrameCount: Int,
    val isReadyForCapture: Boolean
)

/**
 * Dampens detector jitter on the tracked quad and decides how long a lost target stays on screen.
 *
 * ### What this owns, and what it deliberately does not
 *
 * The pipeline has two adjacent responsibilities that are easy to accidentally duplicate:
 *
 * * **Identity** — *which* candidate the user means, and whether it is stable. Owned by
 *   `TargetSelector`. It sees the candidate list, so it is the only component that can tell
 *   "the same document moved" from "a different document appeared".
 * * **Geometry** — *where* that target is, smoothed, and for how long to keep showing it once it is
 *   gone. Owned here.
 *
 * [TemporalTracker] therefore **passes [TrackedTarget.trackingState] through unchanged** rather than
 * re-deriving it from corner velocity. Two components independently deciding "is this stable?"
 * would eventually disagree and produce an overlay that contradicts itself. Velocity-based
 * stabilisation is a refinement of the rule *inside* the selector; it does not belong here.
 *
 * ### Loss behaviour
 *
 * A lost target is not dropped instantly. While the input is `null` the tracker keeps emitting the
 * last smoothed quad with [TrackingState.LOST], so the overlay can fade it out rather than blink
 * off. Once the gap exceeds the configured tolerance it returns `null` — there is nothing left to
 * draw.
 *
 * ### Deviation from plans/007
 *
 * The plan names the package `com.yscanner.camera.tracking` and gives [SmoothedTarget] a
 * `List<PointF>` plus a `NO_TARGET` state. Both are adjusted here:
 *
 * * It lives in `:detection` because `:camera` does not depend on `:detection`, and making the
 *   camera module depend on the detection module to obtain a display type would invert the
 *   dependency direction for no benefit. `:app` depends on both.
 * * It carries a [Quad] rather than four loose points, so it can be handed straight to
 *   `CoordinateTransformer.mapQuad` and so corner ordering is guaranteed by the type.
 * * `NO_TARGET` is expressed as a `null` return instead of an enum constant. A
 *   [SmoothedTarget] with `NO_TARGET` and a non-null `quad` would be self-contradictory, and
 *   nullability already says it exactly once.
 *
 * ARCHITECTURE.md §73 explicitly allows concrete names and boundaries to differ from the sketches.
 */
interface TemporalTracker {
    /**
     * Feeds one frame's selected target through the tracker.
     *
     * @param target the selector's output for this frame, or `null` when it has nothing.
     * @param timestampSeconds monotonic time of this frame, in seconds. Caller-supplied so the
     *   tracker is deterministic and testable without a clock.
     * @return the smoothed target to draw, or `null` when there is nothing left to show.
     */
    fun update(target: TrackedTarget?, timestampSeconds: Double): SmoothedTarget?

    /**
     * Forgets all history and the retained quad, so the next [update] behaves like a cold start
     * (including returning `null` for a `null` input rather than a stale quad).
     */
    fun reset()
}
