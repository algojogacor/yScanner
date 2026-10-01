package com.yscanner.detection.tracking

import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.Quad
import kotlin.math.hypot

/**
 * Which smoothing strategy [QuadSmoother] runs.
 *
 * The project's rule is that filtering strategies are chosen by *measurement*,
 * not intuition, so both are implemented and can be compared on the same input
 * with [JitterStats]:
 *
 * * [ONE_EURO] — speed-adaptive, smooth when still, low-lag when moving. Preferred.
 * * [EXPONENTIAL] — fixed-alpha exponential moving average. Simpler, but a single
 *   alpha forces one fixed trade-off between smoothness and lag.
 */
enum class SmoothingMethod { ONE_EURO, EXPONENTIAL }

/**
 * Tunables shared by every [QuadSmoother].
 *
 * @param minCutoff One Euro baseline cutoff (Hz). **The smoothness-vs-lag knob:
 *   lower smooths more but lags more.**
 * @param beta One Euro speed coefficient. Higher removes lag during fast motion.
 * @param dCutoff One Euro derivative cutoff (Hz).
 * @param exponentialAlpha EMA weight in `(0, 1]` for [SmoothingMethod.EXPONENTIAL].
 *   Lower smooths more but lags more.
 */
data class SmootherConfig(
    val minCutoff: Double = 1.0,
    val beta: Double = 0.05,
    val dCutoff: Double = 1.0,
    val exponentialAlpha: Double = 0.35
) {
    init {
        require(minCutoff.isFinite() && minCutoff > 0.0) {
            "minCutoff must be finite and positive but was $minCutoff"
        }
        require(beta.isFinite() && beta >= 0.0) {
            "beta must be finite and non-negative but was $beta"
        }
        require(dCutoff.isFinite() && dCutoff > 0.0) {
            "dCutoff must be finite and positive but was $dCutoff"
        }
        require(exponentialAlpha.isFinite() && exponentialAlpha > 0.0 && exponentialAlpha <= 1.0) {
            "exponentialAlpha must be within (0, 1] but was $exponentialAlpha"
        }
    }
}

/**
 * Smooths a [Quad] across frames to damp detector jitter before it reaches the
 * camera overlay.
 *
 * Each corner is smoothed **independently in both x and y** — eight scalar
 * channels — using either the speed-adaptive [OneEuroFilter] or a fixed-alpha
 * exponential moving average. Independent channels mean a corner that is still
 * on `x` stays still even while `y` moves.
 *
 * ### What is preserved and what is guarded
 *
 * * **Confidence is passed through untouched.** Smoothing geometry must never
 *   invent confidence, so the emitted corners carry the *raw input's* confidence
 *   values verbatim.
 * * **Non-convex output is rejected.** Independently smoothing eight channels can,
 *   during a fast shape change, blend two convex detections into a self-crossing
 *   quad. When that happens the smoother returns the **previous smoothed quad**
 *   (the last convex quad it emitted) instead of the malformed blend; if there is
 *   no previous quad yet, it returns the **input quad** unchanged. The rejected
 *   blend is discarded from the emitted stream but the filters still advanced, so
 *   the next frame continues from a causal state.
 *
 * The smoother is deterministic: timestamps come from the caller and no clock is
 * read, so a fixed input sequence always yields identical output.
 */
class QuadSmoother(
    private val config: SmootherConfig = SmootherConfig(),
    private val method: SmoothingMethod = SmoothingMethod.ONE_EURO
) {

    /** Eight per-coordinate One Euro filters, or `null` for the exponential method. */
    private val oneEuroChannels: List<OneEuroFilter>? =
        if (method == SmoothingMethod.ONE_EURO) {
            List(CHANNELS) { OneEuroFilter(config.minCutoff, config.beta, config.dCutoff) }
        } else {
            null
        }

    /** Eight per-coordinate EMA accumulators, or `null` for the One Euro method. */
    private val exponentialState: DoubleArray? =
        if (method == SmoothingMethod.EXPONENTIAL) DoubleArray(CHANNELS) else null
    private var exponentialInitialised = false

    /** Last convex quad emitted, used as the fallback for a non-convex blend. */
    private var previousSmoothed: Quad? = null

    /**
     * Smooths [quad] at [timestampSeconds].
     *
     * @return a convex [Quad]. Never `NaN`/`Infinity` for finite input.
     */
    fun smooth(quad: Quad, timestampSeconds: Double): Quad {
        val raw = quad.toArray()
        val smoothed = DoubleArray(CHANNELS)

        when (method) {
            SmoothingMethod.ONE_EURO -> {
                val channels = oneEuroChannels!!
                for (i in 0 until CHANNELS) {
                    smoothed[i] = channels[i].filter(raw[i].toDouble(), timestampSeconds)
                }
            }

            SmoothingMethod.EXPONENTIAL -> {
                val state = exponentialState!!
                val alpha = config.exponentialAlpha
                for (i in 0 until CHANNELS) {
                    val value = if (exponentialInitialised) {
                        alpha * raw[i] + (1.0 - alpha) * state[i]
                    } else {
                        raw[i].toDouble()
                    }
                    state[i] = value
                    smoothed[i] = value
                }
                exponentialInitialised = true
            }
        }

        val candidate = Quad(
            topLeft = Corner(smoothed[0].toFloat(), smoothed[1].toFloat(), quad.topLeft.confidence),
            topRight = Corner(smoothed[2].toFloat(), smoothed[3].toFloat(), quad.topRight.confidence),
            bottomRight = Corner(smoothed[4].toFloat(), smoothed[5].toFloat(), quad.bottomRight.confidence),
            bottomLeft = Corner(smoothed[6].toFloat(), smoothed[7].toFloat(), quad.bottomLeft.confidence)
        )

        return if (candidate.isConvex()) {
            previousSmoothed = candidate
            candidate
        } else {
            // Blend would self-intersect: fall back to the last convex quad we
            // emitted, or to the input if this is the very first frame.
            previousSmoothed ?: quad
        }
    }

    /**
     * Clears every filter's history and the fallback quad. The next [smooth] call
     * behaves like the first call on a fresh instance.
     */
    fun reset() {
        oneEuroChannels?.forEach { it.reset() }
        exponentialState?.fill(0.0)
        exponentialInitialised = false
        previousSmoothed = null
    }

    private companion object {
        /** Four corners × two coordinates. */
        const val CHANNELS = 8
    }
}

/**
 * Quantitative jitter/lag measurement for a sequence of quads.
 *
 * This is the metric the project uses to compare smoothing strategies instead of
 * trusting intuition: a filter is only "better" if it lowers
 * [meanCornerDisplacement] without an unacceptable rise in [meanLag].
 *
 * @param meanCornerDisplacement mean per-corner distance between consecutive
 *   frames. The headline **jitter** number — lower is steadier.
 * @param meanLag mean per-corner distance between the smoothed output and the raw
 *   input over the measured window. A cheap **lag** proxy — lower means the
 *   overlay sits closer to the detector. Only meaningful when produced by
 *   [compare]; [of] reports `0.0` because it has no raw reference.
 * @param frames number of frames considered.
 */
data class JitterStats(
    val meanCornerDisplacement: Double,
    val meanLag: Double,
    val frames: Int
) {
    companion object {
        val EMPTY = JitterStats(0.0, 0.0, 0)

        /**
         * Jitter of [quads] alone, with no raw reference for lag.
         *
         * @param warmupFrames leading frames to discard before measuring, so the
         *   filter's transient does not pollute the steady-state numbers.
         */
        fun of(quads: List<Quad>, warmupFrames: Int = 0): JitterStats {
            val displacement = meanCornerDisplacement(quads, warmupFrames)
            return JitterStats(displacement, 0.0, quads.size)
        }

        /**
         * Compares a smoothed sequence against its raw input, reporting both
         * jitter and lag.
         *
         * @param warmupFrames leading frames to discard before measuring.
         */
        fun compare(
            raw: List<Quad>,
            smoothed: List<Quad>,
            warmupFrames: Int = 0
        ): JitterStats {
            val n = minOf(raw.size, smoothed.size)
            if (n == 0) return EMPTY
            val displacement = meanCornerDisplacement(smoothed.subList(0, n), warmupFrames)
            val lag = meanLag(raw, smoothed, warmupFrames)
            return JitterStats(displacement, lag, n)
        }

        /** Mean per-corner displacement between consecutive frames of [quads]. */
        private fun meanCornerDisplacement(quads: List<Quad>, warmupFrames: Int): Double {
            if (quads.size < 2) return 0.0
            var sum = 0.0
            var count = 0
            for (i in 1 until quads.size) {
                // The pair (i-1, i) is kept only when both frames are past warm-up.
                if (i <= warmupFrames) continue
                sum += meanCornerDistance(quads[i - 1], quads[i])
                count++
            }
            return if (count == 0) 0.0 else sum / count
        }

        /** Mean per-corner distance between corresponding frames of two sequences. */
        private fun meanLag(raw: List<Quad>, smoothed: List<Quad>, warmupFrames: Int): Double {
            val n = minOf(raw.size, smoothed.size)
            var sum = 0.0
            var count = 0
            for (i in 0 until n) {
                if (i < warmupFrames) continue
                sum += meanCornerDistance(raw[i], smoothed[i])
                count++
            }
            return if (count == 0) 0.0 else sum / count
        }

        /** Mean over the four corners of the Euclidean corner displacement. */
        private fun meanCornerDistance(a: Quad, b: Quad): Double {
            val pa = a.toArray()
            val pb = b.toArray()
            var sum = 0.0
            var i = 0
            while (i < pa.size) {
                val dx = (pa[i] - pb[i]).toDouble()
                val dy = (pa[i + 1] - pb[i + 1]).toDouble()
                sum += hypot(dx, dy)
                i += 2
            }
            return sum / 4.0
        }
    }
}
