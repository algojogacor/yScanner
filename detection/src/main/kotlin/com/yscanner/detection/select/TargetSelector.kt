package com.yscanner.detection.select

import com.yscanner.detection.DocumentCandidate
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import com.yscanner.geometry.centroid
import com.yscanner.geometry.pointInPolygon
import com.yscanner.geometry.polygonIoU
import kotlin.math.hypot

/**
 * Lifecycle of the currently selected document target.
 *
 * The names describe *how confident the selector is that this target is a real, stable
 * document*, not how good the underlying detection is:
 *
 * * [ACQUIRING] — a target exists but is not yet confirmed by temporal evidence. It may be
 *   the very first frame a candidate appeared, or a previously held target that was not
 *   matched this frame but is not yet written off.
 * * [TRACKING] — the target was matched against the previous frame, so identity is
 *   continuous, but it has not been seen for long enough to call it stable.
 * * [STABLE] — matched for [TargetSelectorConfig.stableFrameThreshold] consecutive frames.
 * * [LOST] — **not produced by this selector.** [DefaultTargetSelector] reports "no target" as a
 *   `null` return instead, so that "there is nothing to draw" stays reachable downstream: a
 *   selector that answered with a `LOST` placeholder every frame would leave the overlay with no
 *   way to ever stop drawing. This constant is produced by
 *   [com.yscanner.detection.tracking.TemporalTracker], which re-emits the last smoothed quad with
 *   `LOST` while it fades a disappeared target out.
 */
enum class TrackingState { ACQUIRING, TRACKING, STABLE, LOST }

/**
 * The document the scanner currently believes the user means, in frame-space coordinates.
 *
 * @param id identity that is stable across frames while the same document is tracked. A
 *   deliberate target switch (a tap) or a cold start mints a new id, which is how downstream
 *   consumers know the *document* changed rather than merely moved.
 * @param stableFrameCount consecutive frames this identity has been matched. Reset to 0
 *   whenever the target is not matched, and to 1 on a cold start.
 */
data class TrackedTarget(
    val id: String,
    val quad: Quad,
    val confidence: Float,
    val trackingState: TrackingState,
    val stableFrameCount: Int
)

/**
 * Tunables for [DefaultTargetSelector].
 *
 * The weights sum to 1.0 so the heuristic score is directly comparable to a `[0,1]`
 * confidence and easy to reason about. Centrality is weighted highest because the product
 * requirement (PRD §6.3) is to prefer the document the user is pointing at, which is
 * usually the most centred one.
 *
 * @param minIoU minimum IoU for a candidate to count as the same document as the previous
 *   target. Too low and two adjacent documents get merged into one identity; too high and
 *   normal hand shake drops the lock.
 * @param lostFrameThreshold consecutive unmatched frames before the target is given up and
 *   `select` starts returning `null`.
 * @param stableFrameThreshold consecutive matched frames before [TrackingState.STABLE].
 */
data class TargetSelectorConfig(
    val minIoU: Float = 0.3f,
    val lostFrameThreshold: Int = 5,
    val stableFrameThreshold: Int = 3,
    val weightConfidence: Float = 0.25f,
    val weightCentrality: Float = 0.40f,
    val weightArea: Float = 0.25f,
    val weightGeometry: Float = 0.10f
)

/**
 * Chooses which of the visible document candidates the user means, and carries that choice
 * across frames.
 *
 * This is a *pure decision* component: it owns no camera, no model and no clock, so it can be
 * driven deterministically from a unit test. It is intentionally separate from
 * [com.yscanner.detection.DocumentDetector], which only proposes candidates.
 */
interface TargetSelector {
    /**
     * @param candidates candidates for the current frame, in frame-space coordinates.
     * @param previous the target chosen on the previous frame, or `null` on a cold start.
     * @param tapPoint an optional user tap, already mapped into frame space. A tap is both a
     *   selection gesture and (elsewhere) a camera metering instruction.
     * @param frameWidth width of the frame [candidates] and [tapPoint] are expressed in.
     * @param frameHeight height of that frame.
     * @return the selected target, or `null` when there is nothing to select.
     */
    fun select(
        candidates: List<DocumentCandidate>,
        previous: TrackedTarget?,
        tapPoint: PointF?,
        frameWidth: Int,
        frameHeight: Int
    ): TrackedTarget?

    /** Forgets all temporal state so the next [select] behaves like a cold start. */
    fun reset()
}

/**
 * Default [TargetSelector].
 *
 * Selection priority, highest first:
 *
 * 1. **Tap override** — if the user tapped inside a candidate, that candidate wins outright.
 *    A tap is an explicit instruction, so it deliberately overrides temporal persistence;
 *    otherwise the user could never switch away from a locked document.
 * 2. **Temporal match** — the candidate whose quad has the highest IoU with the previous
 *    target, provided it clears [TargetSelectorConfig.minIoU]. Reusing the previous id is
 *    what stops the selection flickering between two visible documents frame to frame, and
 *    it wins even when a *different* candidate would score higher on the heuristic.
 * 3. **Hold** — candidates exist but none matches the previous target: keep the previous
 *    identity for up to [TargetSelectorConfig.lostFrameThreshold] frames so that a single bad
 *    detection frame cannot break the lock. This is what makes persistence robust rather than
 *    merely sticky.
 * 4. **Heuristic** — cold start, or the previous target is confirmed gone: pick the candidate
 *    that is most central, largest, most confident and most quad-like.
 *
 * Determinism: no clock is read and no randomness is used, so the same input sequence always
 * produces the same output. Ids come from a monotonic counter.
 */
class DefaultTargetSelector(
    private val config: TargetSelectorConfig = TargetSelectorConfig()
) : TargetSelector {

    /** Consecutive frames with candidates but no match, or with no candidates at all. */
    private var missedFrames = 0

    /** Monotonic source of target ids. Reset by [reset]. */
    private var idCounter = 0

    override fun select(
        candidates: List<DocumentCandidate>,
        previous: TrackedTarget?,
        tapPoint: PointF?,
        frameWidth: Int,
        frameHeight: Int
    ): TrackedTarget? {
        // 1. Nothing to look at: hold the previous target briefly rather than dropping it at once.
        if (candidates.isEmpty()) {
            if (previous == null) return null
            missedFrames++
            // Past the threshold the document is confirmed gone and there is nothing to select.
            // Returning a placeholder here would make "no target" unreachable for every downstream
            // consumer — the tracker would go on drawing an outline forever. The selector's job is
            // to stop claiming a document it can no longer see; turning that into a fading outline
            // is the tracker's job.
            if (missedFrames >= config.lostFrameThreshold) return null
            return previous.copy(trackingState = TrackingState.ACQUIRING, stableFrameCount = 0)
        }

        // 2. Tap override. Only the containment test matters here; a tap in empty space
        //    falls through and is treated as a metering-only gesture.
        if (tapPoint != null) {
            val tapped = candidates.firstOrNull { pointInPolygon(tapPoint, it.quad.points()) }
            if (tapped != null) {
                missedFrames = 0
                return TrackedTarget(
                    id = nextId(),
                    quad = tapped.quad,
                    confidence = tapped.confidence,
                    trackingState = TrackingState.TRACKING,
                    stableFrameCount = 0
                )
            }
        }

        // 3. Temporal match: keep the identity we already have when possible.
        if (previous != null) {
            val previousPoints = previous.quad.points()
            var best: DocumentCandidate? = null
            var bestIoU = 0f
            for (candidate in candidates) {
                val iou = polygonIoU(previousPoints, candidate.quad.points())
                if (iou > bestIoU) {
                    bestIoU = iou
                    best = candidate
                }
            }
            if (best != null && bestIoU >= config.minIoU) {
                missedFrames = 0
                val stableCount = previous.stableFrameCount + 1
                val state = if (stableCount >= config.stableFrameThreshold) {
                    TrackingState.STABLE
                } else {
                    TrackingState.TRACKING
                }
                return TrackedTarget(
                    id = previous.id,
                    quad = best.quad,
                    confidence = best.confidence,
                    trackingState = state,
                    stableFrameCount = stableCount
                )
            }
        }

        // 3b. Candidates exist but none is the target we were tracking. Hold the previous
        //     identity for a few frames instead of switching immediately.
        //
        //     This is the case that decides whether the lock survives a single bad frame.
        //     Adopting the best-scoring candidate here would break the lock on any one-frame
        //     detection glitch: `previous` becomes the new document, so the old one can never
        //     match again and the overlay flickers between documents. Holding costs nothing
        //     perceptually - at 30fps the whole window is under 200ms - and lets a target that
        //     was only briefly lost resume its identity, and with it its stability count.
        if (previous != null) {
            missedFrames++
            if (missedFrames < config.lostFrameThreshold) {
                return previous.copy(
                    trackingState = TrackingState.ACQUIRING,
                    stableFrameCount = 0
                )
            }
        }

        // 4. Cold start, or the previous target is confirmed gone: choose afresh.
        val chosen = chooseByHeuristic(candidates, frameWidth, frameHeight) ?: return null
        missedFrames = 0
        return TrackedTarget(
            id = nextId(),
            quad = chosen.quad,
            confidence = chosen.confidence,
            trackingState = TrackingState.ACQUIRING,
            stableFrameCount = 1
        )
    }

    override fun reset() {
        missedFrames = 0
        idCounter = 0
    }

    private fun nextId(): String = "target-${++idCounter}"

    /**
     * Picks the candidate with the highest weighted score.
     *
     * Ties are broken by larger quad area, then by earlier position in [candidates], so the
     * result is fully deterministic even for two candidates that score identically.
     */
    private fun chooseByHeuristic(
        candidates: List<DocumentCandidate>,
        frameWidth: Int,
        frameHeight: Int
    ): DocumentCandidate? {
        val frameUsable = frameWidth > 0 && frameHeight > 0
        val centre = if (frameUsable) PointF(frameWidth / 2f, frameHeight / 2f) else null
        val halfDiagonal = if (frameUsable) hypot(frameWidth.toDouble(), frameHeight.toDouble()) / 2.0 else 0.0
        val frameArea = if (frameUsable) frameWidth.toFloat() * frameHeight.toFloat() else 0f

        var best: DocumentCandidate? = null
        var bestScore = Float.NEGATIVE_INFINITY
        var bestArea = 0f
        for (candidate in candidates) {
            val score = scoreOf(candidate, centre, halfDiagonal, frameArea, frameUsable)
            val area = candidate.quad.area()
            // Exact tie: prefer the larger quad. Equal area keeps the earlier candidate,
            // because `better` stays false and we never overwrite.
            val better = score > bestScore || (score == bestScore && area > bestArea)
            if (better) {
                best = candidate
                bestScore = score
                bestArea = area
            }
        }
        return best
    }

    private fun scoreOf(
        candidate: DocumentCandidate,
        centre: PointF?,
        halfDiagonal: Double,
        frameArea: Float,
        frameUsable: Boolean
    ): Float {
        val confidence = candidate.confidence.coerceIn(0f, 1f)

        val centrality = if (frameUsable && centre != null && halfDiagonal > 0.0) {
            val distance = candidate.quad.centroid().distanceTo(centre).toDouble()
            (1.0 - distance / halfDiagonal).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        val areaTerm = if (frameUsable && frameArea > 0f) {
            (candidate.quad.area() / frameArea).coerceIn(0f, 1f)
        } else {
            0f
        }

        val geometryTerm = if (candidate.quad.isConvex()) 1f else 0f

        return config.weightConfidence * confidence +
            config.weightCentrality * centrality +
            config.weightArea * areaTerm +
            config.weightGeometry * geometryTerm
    }
}

/** The quad's four corners as a plain point list, for the geometry helpers. */
private fun Quad.points(): List<PointF> = listOf(
    topLeft.toPointF(),
    topRight.toPointF(),
    bottomRight.toPointF(),
    bottomLeft.toPointF()
)
