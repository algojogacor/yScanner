package com.yscanner.detection.bench

import com.yscanner.detection.model.ModelInput
import com.yscanner.detection.model.SegmentationModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Percentile summary of a set of latency samples, in milliseconds.
 */
data class LatencyStats(
    val samples: Int,
    val meanMs: Double,
    val minMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    /** Throughput implied by [meanMs]. 0 when [meanMs] is 0. */
    val impliedFps: Double
) {
    companion object {
        val EMPTY = LatencyStats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /** Builds stats from raw millisecond samples. */
        fun from(samplesMs: List<Double>): LatencyStats {
            if (samplesMs.isEmpty()) return EMPTY
            val sorted = samplesMs.sorted()
            val mean = sorted.sum() / sorted.size
            return LatencyStats(
                samples = sorted.size,
                meanMs = mean,
                minMs = sorted.first(),
                p50Ms = percentile(sorted, 50.0),
                p95Ms = percentile(sorted, 95.0),
                p99Ms = percentile(sorted, 99.0),
                maxMs = sorted.last(),
                impliedFps = if (mean > 0) 1000.0 / mean else 0.0
            )
        }

        /** Linear-interpolated percentile over an already-sorted list. */
        private fun percentile(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return 0.0
            if (sorted.size == 1) return sorted[0]
            val rank = (p / 100.0) * (sorted.size - 1)
            val lower = kotlin.math.floor(rank).toInt()
            val upper = kotlin.math.ceil(rank).toInt()
            if (lower == upper) return sorted[lower]
            val weight = rank - lower
            return sorted[lower] * (1 - weight) + sorted[upper] * weight
        }
    }
}

/**
 * Result of one benchmark run.
 *
 * @param validatedOnDevice **Critical field.** A benchmark executed on a
 *   developer machine says nothing about mid-range Android performance. The
 *   project's rules forbid presenting desktop or emulator numbers as device
 *   results, so every report carries its provenance explicitly and
 *   [LatencyStats] alone must never be quoted without it.
 */
data class BenchmarkReport(
    val modelId: String,
    val descriptorSummary: String,
    val warmupIterations: Int,
    val measuredIterations: Int,
    val latency: LatencyStats,
    val validatedOnDevice: Boolean,
    val deviceLabel: String,
    /** Heap delta across the run in bytes, or null when unavailable. */
    val heapDeltaBytes: Long? = null,
    val notes: List<String> = emptyList()
) {
    /** Human-readable, provenance-explicit one-liner. */
    fun summary(): String {
        val provenance = if (validatedOnDevice) {
            "validated on $deviceLabel"
        } else {
            "NOT validated on target device ($deviceLabel)"
        }
        return "$modelId: mean=${"%.2f".format(latency.meanMs)}ms " +
            "p50=${"%.2f".format(latency.p50Ms)}ms " +
            "p95=${"%.2f".format(latency.p95Ms)}ms " +
            "p99=${"%.2f".format(latency.p99Ms)}ms " +
            "n=${latency.samples} impliedFps=${"%.1f".format(latency.impliedFps)} [$provenance]"
    }
}

/**
 * Measures inference latency of a [SegmentationModel].
 *
 * Exists because the project's engineering rules require performance to be
 * *measured, never asserted*. Spike S01 could not produce a single on-device
 * number for any candidate model, so this harness is the instrument that will
 * generate them once a model and a target device are chosen.
 *
 * Usage on a device is the only mode that produces a decision-grade result;
 * [deviceLabel] and [validatedOnDevice] exist so that a desktop run cannot be
 * mistaken for one.
 */
class InferenceBenchmark(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Runs [measuredIterations] inferences after [warmupIterations] discarded
     * warm-up passes.
     *
     * Warm-up matters: the first inference of a TFLite/LiteRT model typically
     * includes delegate initialisation and is one to two orders of magnitude
     * slower than steady state. Including it would produce a meaningless mean.
     */
    suspend fun run(
        model: SegmentationModel,
        input: ModelInput,
        warmupIterations: Int = 10,
        measuredIterations: Int = 100,
        validatedOnDevice: Boolean = false,
        deviceLabel: String = "unknown",
        notes: List<String> = emptyList()
    ): BenchmarkReport = withContext(dispatcher) {
        require(warmupIterations >= 0) { "warmupIterations must not be negative" }
        require(measuredIterations > 0) { "measuredIterations must be positive" }

        model.load()

        repeat(warmupIterations) {
            input.rewind()
            model.infer(input)
        }

        val runtime = Runtime.getRuntime()
        // Best-effort: GC before sampling so the delta reflects the run, not prior garbage.
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        val samples = ArrayList<Double>(measuredIterations)
        repeat(measuredIterations) {
            input.rewind()
            val started = System.nanoTime()
            model.infer(input)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
            samples += elapsedMs
        }

        val heapAfter = runtime.totalMemory() - runtime.freeMemory()

        BenchmarkReport(
            modelId = model.descriptor.id,
            descriptorSummary = "${model.descriptor.inputWidth}x${model.descriptor.inputHeight}" +
                "x${model.descriptor.inputChannels} ${model.descriptor.dtype}" +
                " -> ${model.descriptor.outputKind}",
            warmupIterations = warmupIterations,
            measuredIterations = measuredIterations,
            latency = LatencyStats.from(samples),
            validatedOnDevice = validatedOnDevice,
            deviceLabel = deviceLabel,
            heapDeltaBytes = heapAfter - heapBefore,
            notes = notes
        )
    }
}

/**
 * Rolling latency monitor for use during a live camera session.
 *
 * Unlike [InferenceBenchmark] this is cheap, allocation-light, and bounded, so
 * it can run continuously without affecting the thing it measures. Used to drive
 * the "dropped analysis frames" and effective-FPS diagnostics the PRD asks for.
 */
class RollingLatencyMonitor(private val windowSize: Int = 60) {
    private val samples = ArrayDeque<Double>(windowSize)
    private var droppedFrames = 0L
    private var totalFrames = 0L

    init {
        require(windowSize > 0) { "windowSize must be positive" }
    }

    /** Records one successful inference of [latencyMs]. */
    @Synchronized
    fun record(latencyMs: Double) {
        totalFrames++
        if (samples.size == windowSize) samples.removeFirst()
        samples.addLast(latencyMs)
    }

    /** Records that a frame was skipped because the previous one was still running. */
    @Synchronized
    fun recordDrop() {
        totalFrames++
        droppedFrames++
    }

    /** Snapshot of the current window. */
    @Synchronized
    fun stats(): LatencyStats = LatencyStats.from(samples.toList())

    /** Fraction of frames dropped in the current window, in `[0,1]`. */
    @Synchronized
    fun dropRate(): Float =
        if (totalFrames == 0L) 0f else droppedFrames.toFloat() / totalFrames.toFloat()

    @Synchronized
    fun reset() {
        samples.clear()
        droppedFrames = 0
        totalFrames = 0
    }
}
