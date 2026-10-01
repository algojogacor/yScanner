package com.yscanner.detection.model

import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.FakeSegmentationModel
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelManagerTest {

    @Test
    fun `descriptor is available without loading weights`() = runTest {
        val manager = ModelManager { FakeSegmentationModel() }

        assertThat(manager.descriptor.id).isEqualTo("fake-model")
        assertThat(manager.current).isNull()
    }

    @Test
    fun `ensureLoaded loads exactly once across repeated calls`() = runTest {
        var created = 0
        val manager = ModelManager { FakeSegmentationModel().also { created++ } }

        val first = manager.ensureLoaded()
        val second = manager.ensureLoaded()

        assertThat(first).isSameInstanceAs(second)
        assertThat(created).isEqualTo(1)
        assertThat((first as FakeSegmentationModel).loadCount).isEqualTo(1)
        assertThat(first.state).isEqualTo(ModelState.READY)
    }

    @Test
    fun `a failed load is retried rather than cached forever`() = runTest {
        // First instance fails to load; the manager must not wedge itself, because
        // transient delegate failures are common on mid-range devices.
        var attempt = 0
        val manager = ModelManager {
            attempt++
            FakeSegmentationModel(failOnLoad = attempt == 1)
        }

        val firstFailure = runCatching { manager.ensureLoaded() }
        assertThat(firstFailure.isFailure).isTrue()

        val recovered = manager.ensureLoaded()
        assertThat(recovered.state).isEqualTo(ModelState.READY)
        assertThat(attempt).isEqualTo(2)
    }

    @Test
    fun `release closes the model and allows a later reload`() = runTest {
        var created = 0
        val manager = ModelManager { FakeSegmentationModel().also { created++ } }

        val first = manager.ensureLoaded() as FakeSegmentationModel
        manager.release()

        assertThat(first.closeCount).isEqualTo(1)
        assertThat(manager.current).isNull()

        val second = manager.ensureLoaded()
        assertThat(second).isNotSameInstanceAs(first)
        assertThat(created).isEqualTo(2)
    }

    @Test
    fun `release before any load is safe`() = runTest {
        val manager = ModelManager { FakeSegmentationModel() }
        manager.release()
        manager.release()
        assertThat(manager.current).isNull()
    }

    @Test
    fun `model state transitions are observable`() = runTest {
        val model = FakeSegmentationModel()
        assertThat(model.state).isEqualTo(ModelState.UNLOADED)

        model.load()
        assertThat(model.state).isEqualTo(ModelState.READY)

        model.close()
        assertThat(model.state).isEqualTo(ModelState.CLOSED)
    }

    @Test
    fun `infer before load is rejected`() = runTest {
        val model = FakeSegmentationModel()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)

        val result = runCatching { model.infer(input) }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `close is idempotent`() = runTest {
        val model = FakeSegmentationModel()
        model.load()
        model.close()
        model.close()
        assertThat(model.closeCount).isEqualTo(2)
        assertThat(model.state).isEqualTo(ModelState.CLOSED)
    }

    @Test
    fun `inference failure surfaces as ModelLoadException`() = runTest {
        val model = FakeSegmentationModel(failOnInfer = true)
        model.load()
        val input = ModelInput(java.nio.ByteBuffer.allocate(0), model.descriptor)

        val result = runCatching { model.infer(input) }
        assertThat(result.exceptionOrNull()).isInstanceOf(ModelLoadException::class.java)
    }
}
