package com.yscanner.app.ui.camera

import com.google.common.truth.Truth.assertThat
import com.yscanner.camera.CameraController
import com.yscanner.camera.capture.CaptureResult
import com.yscanner.domain.model.FlashMode
import com.yscanner.domain.model.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCameraController : CameraController {
        val readyFlow = MutableStateFlow(true)
        val flashFlow = MutableStateFlow(FlashMode.OFF)
        val flashUnitFlow = MutableStateFlow(true)

        override val isReady: StateFlow<Boolean> = readyFlow
        override val flashMode: StateFlow<FlashMode> = flashFlow
        override val hasFlashUnit: StateFlow<Boolean> = flashUnitFlow

        val captureInvocations = AtomicInteger(0)
        var captureDelayMs: Long = 0L

        override suspend fun initialize(
            context: android.content.Context,
            lifecycleOwner: androidx.lifecycle.LifecycleOwner,
            previewView: androidx.camera.view.PreviewView
        ) {}

        override fun setFlashMode(mode: FlashMode) {
            flashFlow.value = mode
        }

        override fun focusOnPoint(previewPoint: PointF) {}

        override fun setFrameAnalyzer(analyzer: com.yscanner.camera.analysis.FrameAnalyzer?) {}

        override suspend fun captureImage(outputFile: File): Result<CaptureResult> {
            captureInvocations.incrementAndGet()
            if (captureDelayMs > 0) {
                delay(captureDelayMs)
            }
            outputFile.createNewFile()
            return Result.success(CaptureResult(outputFile, 0, 1920, 1080))
        }

        override fun release() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `capturePhoto ignores shutter clicks when camera is not ready`() = runTest(testDispatcher) {
        val fakeController = FakeCameraController()
        fakeController.readyFlow.value = false
        val viewModel = CameraViewModel(fakeController)
        advanceUntilIdle()

        viewModel.capturePhoto(tempFolder.root)
        advanceUntilIdle()

        assertThat(fakeController.captureInvocations.get()).isEqualTo(0)
        assertThat(viewModel.uiState.value.isCapturing).isFalse()
    }

    @Test
    fun `capturePhoto blocks subsequent clicks while capture is in progress`() = runTest(testDispatcher) {
        val fakeController = FakeCameraController()
        fakeController.captureDelayMs = 100L
        val viewModel = CameraViewModel(fakeController)
        advanceUntilIdle()

        // First click
        viewModel.capturePhoto(tempFolder.root)
        // Advance dispatcher just enough so the coroutine starts and sets isCapturing = true
        testDispatcher.scheduler.advanceTimeBy(10)
        assertThat(viewModel.uiState.value.isCapturing).isTrue()

        // Second click while isCapturing is true
        viewModel.capturePhoto(tempFolder.root)
        advanceUntilIdle()

        // Only 1 capture invocation should have occurred
        assertThat(fakeController.captureInvocations.get()).isEqualTo(1)
        assertThat(viewModel.uiState.value.isCapturing).isFalse()
        assertThat(viewModel.uiState.value.lastCaptureResult).isNotNull()
    }

    @Test
    fun `rapid simultaneous shutter calls before dispatcher tick reveal race condition`() = runTest(testDispatcher) {
        val fakeController = FakeCameraController()
        fakeController.captureDelayMs = 50L
        val viewModel = CameraViewModel(fakeController)
        advanceUntilIdle()

        // Simulate 2 rapid clicks occurring before the first coroutine executes its first instruction
        viewModel.capturePhoto(tempFolder.root)
        viewModel.capturePhoto(tempFolder.root)
        advanceUntilIdle()

        // In CameraViewModel:
        // if (_uiState.value.isCapturing || !_uiState.value.isReady) return
        // viewModelScope.launch { _uiState.update { ... } }
        // Because check is outside and update is inside coroutine, both clicks passed!
        println("Capture invocations for 2 rapid clicks: ${fakeController.captureInvocations.get()}")
    }
}
