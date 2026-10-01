package com.yscanner.detection

import com.yscanner.detection.model.ModelDescriptor
import com.yscanner.detection.model.ModelInput
import com.yscanner.detection.model.ModelLoadException
import com.yscanner.detection.model.ModelState
import com.yscanner.detection.model.OutputKind
import com.yscanner.detection.model.SegmentationModel
import com.yscanner.detection.model.SegmentationOutput

/**
 * A deterministic, weight-free [SegmentationModel] for tests.
 *
 * yScanner has no trained model yet (spike S01 is still open at its decision
 * gate). Without a fake, none of the detection pipeline could be tested at all.
 * This produces reproducible outputs so the pipeline, lifecycle handling and
 * failure paths can be verified now, and a real model can be dropped in later
 * without changing a single test.
 *
 * Deliberately lives in the `:detection` test source set rather than
 * `:test-fixtures`: `:detection` already depends on `:test-fixtures`, so the
 * reverse dependency would be a cycle.
 */
class FakeSegmentationModel(
    override val descriptor: ModelDescriptor = defaultDescriptor(),
    /** Value written into foreground pixels of the synthetic mask. */
    private val foregroundValue: Float = 0.9f,
    private val backgroundValue: Float = 0.05f,
    /** When true, [load] throws — used to test degradation paths. */
    private val failOnLoad: Boolean = false,
    /** When true, [infer] throws — used to test runtime failure handling. */
    private val failOnInfer: Boolean = false,
    /** Synthetic inference duration in nanoseconds; 0 for "as fast as possible". */
    private val simulatedNanos: Long = 0L
) : SegmentationModel {

    override var state: ModelState = ModelState.UNLOADED
        private set

    override var failureCause: Throwable? = null
        private set

    var loadCount: Int = 0
        private set
    var closeCount: Int = 0
        private set
    var inferCount: Int = 0
        private set

    override suspend fun load() {
        if (state == ModelState.READY) return
        check(state != ModelState.CLOSED) { "cannot load a closed model" }
        state = ModelState.LOADING
        loadCount++
        if (failOnLoad) {
            failureCause = ModelLoadException("simulated load failure")
            state = ModelState.FAILED
            throw failureCause as ModelLoadException
        }
        state = ModelState.READY
    }

    override suspend fun infer(input: ModelInput): SegmentationOutput {
        check(state == ModelState.READY) { "model is $state, expected READY" }
        inferCount++
        if (failOnInfer) throw ModelLoadException("simulated inference failure")
        if (simulatedNanos > 0) {
            val deadline = System.nanoTime() + simulatedNanos
            @Suppress("ControlFlowWithEmptyBody")
            while (System.nanoTime() < deadline) {
                // Busy-wait so the harness measures a realistic duration.
            }
        }

        val planeCount = if (descriptor.outputKind == OutputKind.CORNER_HEATMAP) 4 else 1
        val planeSize = descriptor.outputWidth * descriptor.outputHeight
        val confidence = FloatArray(planeSize * planeCount)

        when (descriptor.outputKind) {
            OutputKind.MASK, OutputKind.EDGE_MAP -> {
                // Centred rectangle covering the middle half of the frame.
                val w = descriptor.outputWidth
                val h = descriptor.outputHeight
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val inside = x >= w / 4 && x < w * 3 / 4 && y >= h / 4 && y < h * 3 / 4
                        confidence[y * w + x] = if (inside) foregroundValue else backgroundValue
                    }
                }
            }
            OutputKind.CORNER_HEATMAP -> {
                // One impulse per corner plane, placed at the middle-half corners.
                val w = descriptor.outputWidth
                val h = descriptor.outputHeight
                val points = listOf(
                    0 to (w / 4 to h / 4),
                    1 to (w * 3 / 4 to h / 4),
                    2 to (w * 3 / 4 to h * 3 / 4),
                    3 to (w / 4 to h * 3 / 4)
                )
                for ((plane, pt) in points) {
                    val (px, py) = pt
                    confidence[plane * planeSize + py * w + px] = foregroundValue
                }
            }
        }

        return SegmentationOutput(
            descriptor = descriptor,
            confidence = confidence,
            documentPresent = true,
            inferenceLatencyMs = simulatedNanos / 1_000_000
        )
    }

    override fun close() {
        closeCount++
        state = ModelState.CLOSED
    }

    companion object {
        fun defaultDescriptor(
            width: Int = 64,
            height: Int = 64,
            outputKind: OutputKind = OutputKind.MASK
        ) = ModelDescriptor(
            id = "fake-model",
            version = "test",
            inputWidth = width,
            inputHeight = height,
            outputKind = outputKind,
            outputWidth = width,
            outputHeight = height
        )
    }
}
