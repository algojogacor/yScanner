package com.yscanner.detection.bench

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.FakeSegmentationModel
import com.yscanner.detection.model.ModelInput
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InferenceBenchmarkTest {

    @Test
    fun `empty samples produce empty stats`() {
        assertThat(LatencyStats.from(emptyList())).isEqualTo(LatencyStats.EMPTY)
    }

    @Test
    fun `stats compute mean min max and fps`() {
        val stats = LatencyStats.from(listOf(10.0, 20.0, 30.0, 40.0))

        assertThat(stats.samples).isEqualTo(4)
        assertThat(stats.meanMs).isWithin(0.001).of(25.0)
        assertThat(stats.minMs).isEqualTo(10.0)
        assertThat(stats.maxMs).isEqualTo(40.0)
        assertThat(stats.impliedFps).isWithin(0.01).of(40.0)
    }

    @Test
    fun `percentiles interpolate on the sorted distribution`() {
        val samples = (1..100).map { it.toDouble() }
        val stats = LatencyStats.from(samples)

        // p50 of 1..100 sits between 50 and 51.
        assertThat(stats.p50Ms).isWithin(0.6).of(50.5)
        // p95 sits between 95 and 96.
        assertThat(stats.p95Ms).isWithin(0.6).of(95.5)
        assertThat(stats.p99Ms).isWithin(0.6).of(99.0)
    }

    @Test
    fun `single sample is handled`() {
        val stats = LatencyStats.from(listOf(12.5))
        assertThat(stats.samples).isEqualTo(1)
        assertThat(stats.p50Ms).isEqualTo(12.5)
        assertThat(stats.p99Ms).isEqualTo(12.5)
    }

    @Test
    fun `benchmark discards warmup and records the measured count`() = runTest {
        val model = FakeSegmentationModel()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)

        val report = InferenceBenchmark().run(
            model = model,
            input = input,
            warmupIterations = 3,
            measuredIterations = 7,
            validatedOnDevice = false,
            deviceLabel = "desktop-jvm"
        )

        assertThat(report.latency.samples).isEqualTo(7)
        // 3 warm-up + 7 measured inferences.
        assertThat(model.inferCount).isEqualTo(10)
        assertThat(report.modelId).isEqualTo("fake-model")
    }

    @Test
    fun `report never claims device validation unless told so`() = runTest {
        val model = FakeSegmentationModel()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)

        val report = InferenceBenchmark().run(model, input, warmupIterations = 0, measuredIterations = 2)

        assertThat(report.validatedOnDevice).isFalse()
        assertThat(report.summary()).contains("NOT validated on target device")
    }

    @Test
    fun `report labels device provenance when validated on hardware`() = runTest {
        val model = FakeSegmentationModel()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)

        val report = InferenceBenchmark().run(
            model, input,
            warmupIterations = 0,
            measuredIterations = 2,
            validatedOnDevice = true,
            deviceLabel = "Pixel 6a"
        )

        assertThat(report.summary()).contains("validated on Pixel 6a")
    }

    @Test
    fun `rolling monitor tracks drops and window statistics`() {
        val monitor = RollingLatencyMonitor(windowSize = 3)
        monitor.record(10.0)
        monitor.record(20.0)
        monitor.recordDrop()

        assertThat(monitor.stats().samples).isEqualTo(2)
        assertThat(monitor.dropRate()).isWithin(0.001f).of(1f / 3f)

        // recordDrop() counts a frame but contributes no latency sample, so the
        // window holds [10, 20, 30] and the minimum is still 10.
        monitor.record(30.0)
        assertThat(monitor.stats().samples).isEqualTo(3)
        assertThat(monitor.stats().minMs).isEqualTo(10.0)

        // Window is bounded at 3: a fourth latency sample evicts the oldest.
        monitor.record(40.0)
        assertThat(monitor.stats().samples).isEqualTo(3)
        assertThat(monitor.stats().minMs).isEqualTo(20.0)
        assertThat(monitor.stats().maxMs).isEqualTo(40.0)

        monitor.reset()
        assertThat(monitor.stats().samples).isEqualTo(0)
        assertThat(monitor.dropRate()).isEqualTo(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `benchmark rejects a non positive iteration count`() = runTest {
        val model = FakeSegmentationModel()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)
        InferenceBenchmark().run(model, input, warmupIterations = 0, measuredIterations = 0)
    }
}
