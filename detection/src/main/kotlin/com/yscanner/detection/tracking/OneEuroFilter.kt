package com.yscanner.detection.tracking

import com.yscanner.domain.model.PointF
import kotlin.math.PI
import kotlin.math.abs

/**
 * The **One Euro filter** (Casiez, Roussel & Vogel — *"1€ Filter: A Simple
 * Speed-based Low-Pass Filter for Noisy Input in Interactive Systems"*, CHI 2012)
 * applied to a single scalar signal.
 *
 * The filter is an exponential low-pass whose cutoff frequency is raised while
 * the signal is moving quickly and lowered while it is nearly still:
 *
 * ```
 * tau(cutoff)      = 1 / (2 * PI * cutoff)
 * alpha(cutoff, dt)= 1 / (1 + tau(cutoff) / dt)          // in (0, 1)
 * dx               = (x - x_prev) / dt                   // raw derivative
 * dx_hat           = lowPass(dx,   alpha(dCutoff, dt))   // smoothed derivative
 * cutoff           = minCutoff + beta * |dx_hat|         // adaptive cutoff
 * y                = lowPass(x,    alpha(cutoff, dt))    // filtered output
 * ```
 *
 * ### The parameter trade-off (this is the knob that trades smoothness against lag)
 *
 * * [minCutoff] — the baseline cutoff. **Lowering it smooths more but adds lag**;
 *   raising it tracks faster but lets more jitter through. This is the primary
 *   smoothness-vs-lag control.
 * * [beta] — the speed coefficient. **Raising it removes lag during fast motion**
 *   at the cost of letting more high-frequency noise through while the target is
 *   moving. It is the reason the filter can be both smooth when still and
 *   responsive when moving.
 * * [dCutoff] — the cutoff of the derivative estimate. Rarely needs changing;
 *   it only controls how reactive the *speed estimate* is.
 *
 * ### Determinism
 *
 * Timestamps are **supplied by the caller** — the filter never reads a clock, so
 * it is fully deterministic and unit-testable. Two callers feeding the same
 * `(value, timestamp)` sequence get bit-identical output.
 *
 * ### Robustness
 *
 * The filter never emits `NaN` or `Infinity`:
 * * a non-finite input [value] is ignored and the previous filtered value is
 *   returned;
 * * a non-finite or non-positive `dt` (repeated or out-of-order timestamps) is
 *   treated as "no time has passed": the previous filtered value is returned and
 *   the internal state is left untouched, so the next well-formed sample still
 *   measures `dt` from the last *valid* timestamp.
 */
class OneEuroFilter(
    /** Baseline cutoff frequency in Hz. Lower = smoother, but laggier. */
    val minCutoff: Double = 1.0,
    /** Speed coefficient. Higher = less lag on fast motion, more noise passed. */
    val beta: Double = 0.0,
    /** Cutoff frequency in Hz for the derivative estimate. */
    val dCutoff: Double = 1.0
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
    }

    private var hasState = false
    private var lastTimestamp = 0.0
    private var lastRawValue = 0.0
    private var lastFilteredValue = 0.0
    private var lastFilteredDerivative = 0.0

    /**
     * Feeds one sample through the filter.
     *
     * @param value the raw signal value for this sample.
     * @param timestampSeconds monotonic time of the sample, in seconds.
     * @return the filtered value; the first sample is returned verbatim.
     */
    fun filter(value: Double, timestampSeconds: Double): Double {
        // A malformed sample must not poison the state or escape as NaN/Infinity.
        if (!value.isFinite()) return lastFilteredValue

        if (!hasState) {
            // Initialisation: there is no history to low-pass against, so the
            // first sample passes through unchanged and seeds the derivative at 0.
            hasState = true
            lastTimestamp = timestampSeconds
            lastRawValue = value
            lastFilteredValue = value
            lastFilteredDerivative = 0.0
            return value
        }

        val dt = timestampSeconds - lastTimestamp
        if (!dt.isFinite() || dt <= 0.0) {
            // No usable time step (duplicate or out-of-order timestamp). Reuse the
            // previous filtered value and leave every piece of state untouched so
            // the next valid sample is differenced against the last valid one.
            return lastFilteredValue
        }

        val rawDerivative = (value - lastRawValue) / dt
        val derivativeAlpha = alpha(dCutoff, dt)
        val filteredDerivative =
            lowPass(derivativeAlpha, rawDerivative, lastFilteredDerivative)

        // Adaptive cutoff: the faster the motion, the higher the cutoff (less lag).
        val cutoff = minCutoff + beta * abs(filteredDerivative)
        val valueAlpha = alpha(cutoff, dt)
        val filteredValue = lowPass(valueAlpha, value, lastFilteredValue)

        lastTimestamp = timestampSeconds
        lastRawValue = value
        lastFilteredValue = filteredValue
        lastFilteredDerivative = filteredDerivative
        return filteredValue
    }

    /**
     * Clears all state. The next [filter] call behaves exactly like the first
     * call on a fresh instance (returns its input verbatim). Used when the
     * tracked target changes, so history from the old target is not blended in.
     */
    fun reset() {
        hasState = false
        lastTimestamp = 0.0
        lastRawValue = 0.0
        lastFilteredValue = 0.0
        lastFilteredDerivative = 0.0
    }

    /** The most recent filtered value, or 0.0 if nothing has been filtered yet. */
    fun currentValue(): Double = lastFilteredValue

    /** Exponential smoothing factor for [cutoff] at time step [dt]; always in (0, 1). */
    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lowPass(alpha: Double, value: Double, previous: Double): Double =
        alpha * value + (1.0 - alpha) * previous

    companion object {
        /** Convenience factory mirroring the positional parameter order. */
        fun of(minCutoff: Double, beta: Double, dCutoff: Double = 1.0): OneEuroFilter =
            OneEuroFilter(minCutoff, beta, dCutoff)
    }
}

/**
 * Applies two independent [OneEuroFilter]s to the `x` and `y` channels of a
 * [PointF], sharing one parameter set.
 *
 * The two axes are filtered independently on purpose: a document can pan on one
 * axis while being still on the other, and a shared speed estimate would let
 * motion on `x` wrongly desensitise `y`. Both channels share the same timestamps,
 * so callers must pass a monotonic time base.
 */
class PointFOneEuroFilter(
    minCutoff: Double = 1.0,
    beta: Double = 0.0,
    dCutoff: Double = 1.0
) {
    private val xFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val yFilter = OneEuroFilter(minCutoff, beta, dCutoff)

    /**
     * Filters [point] at [timestampSeconds].
     *
     * @return a new [PointF] whose coordinates are the filtered `x`/`y`.
     */
    fun filter(point: PointF, timestampSeconds: Double): PointF = PointF(
        x = xFilter.filter(point.x.toDouble(), timestampSeconds).toFloat(),
        y = yFilter.filter(point.y.toDouble(), timestampSeconds).toFloat()
    )

    /** Clears both axis filters; the next call behaves like a fresh instance. */
    fun reset() {
        xFilter.reset()
        yFilter.reset()
    }
}
