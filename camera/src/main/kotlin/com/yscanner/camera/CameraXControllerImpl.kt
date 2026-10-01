package com.yscanner.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.yscanner.camera.analysis.FrameAnalyzer
import com.yscanner.camera.capture.CaptureManager
import com.yscanner.camera.capture.CaptureResult
import com.yscanner.camera.capture.FileCaptureManager
import com.yscanner.camera.executor.CameraExecutor
import com.yscanner.camera.util.await
import com.yscanner.domain.model.FlashMode
import com.yscanner.domain.model.PointF
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "CameraXController"

class CameraXControllerImpl(
    private val captureManager: CaptureManager = FileCaptureManager(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : CameraController, DefaultLifecycleObserver {

    private val mutex = Mutex()

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    override val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)
    override val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

    // CameraX components
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var currentPreviewView: PreviewView? = null
    private var currentLifecycleOwner: LifecycleOwner? = null

    // Concurrency & Analysis
    private var cameraExecutor = CameraExecutor()
    @Volatile
    private var activeAnalyzer: FrameAnalyzer? = null

    override suspend fun initialize(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Unit = withContext(mainDispatcher) {
        mutex.withLock {
            try {
                releaseInternal()

                if (cameraExecutor.isShutdown) {
                    cameraExecutor = CameraExecutor()
                }

                currentPreviewView = previewView
                currentLifecycleOwner = lifecycleOwner
                lifecycleOwner.lifecycle.addObserver(this@CameraXControllerImpl)

                val provider = ProcessCameraProvider.getInstance(context).await()
                cameraProvider = provider

                // 1. Configure Preview UseCase
                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                preview = previewUseCase

                // 2. Configure ImageAnalysis UseCase (STRATEGY_KEEP_ONLY_LATEST)
                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        activeAnalyzer?.analyze(imageProxy)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Exception during frame analysis", t)
                    } finally {
                        // Strict guarantee: zero frame leakage
                        imageProxy.close()
                    }
                }
                imageAnalysis = analysisUseCase

                // 3. Configure ImageCapture UseCase (MAXIMIZE_QUALITY)
                val initialFlash = _flashMode.value
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(
                        if (initialFlash == FlashMode.ON) ImageCapture.FLASH_MODE_ON
                        else ImageCapture.FLASH_MODE_OFF
                    )
                    .build()
                imageCapture = captureUseCase

                // 4. Bind UseCases to Lifecycle
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()

                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase,
                    captureUseCase
                )
                camera = boundCamera

                val hasFlash = boundCamera.cameraInfo.hasFlashUnit()
                _hasFlashUnit.value = hasFlash

                // Apply initial flash / torch settings
                applyFlashMode(initialFlash, boundCamera)

                _isReady.value = true
                Log.i(TAG, "CameraX successfully initialized and bound to lifecycle.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX", e)
                _isReady.value = false
                throw e
            }
        }
    }

    override fun setFlashMode(mode: FlashMode) {
        _flashMode.value = mode
        camera?.let { applyFlashMode(mode, it) }
    }

    private fun applyFlashMode(mode: FlashMode, cam: Camera) {
        val hasFlash = cam.cameraInfo.hasFlashUnit()
        val control: CameraControl = cam.cameraControl
        val capture = imageCapture

        if (!hasFlash && (mode == FlashMode.ON || mode == FlashMode.TORCH)) {
            Log.w(TAG, "Device does not have a physical flash unit. Ignoring $mode.")
            return
        }

        when (mode) {
            FlashMode.OFF -> {
                control.enableTorch(false)
                capture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashMode.ON -> {
                control.enableTorch(false)
                capture?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            FlashMode.TORCH -> {
                capture?.flashMode = ImageCapture.FLASH_MODE_OFF
                control.enableTorch(true)
            }
        }
    }

    override fun focusOnPoint(previewPoint: PointF) {
        val cam = camera ?: return
        val previewView = currentPreviewView ?: return

        try {
            val factory = previewView.meteringPointFactory
            val meteringPoint = factory.createPoint(previewPoint.x, previewPoint.y)

            val action = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute focus metering on point $previewPoint", e)
        }
    }

    override fun setFrameAnalyzer(analyzer: FrameAnalyzer?) {
        this.activeAnalyzer = analyzer
    }

    override suspend fun captureImage(outputFile: File): Result<CaptureResult> {
        val capture = imageCapture ?: return Result.failure(
            IllegalStateException("ImageCapture is not initialized or bound")
        )
        return captureManager.captureToFile(capture, outputFile)
    }

    override fun release() {
        releaseInternal()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    private fun releaseInternal() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            camera = null
            preview = null
            imageAnalysis = null
            imageCapture = null
            currentPreviewView = null
            currentLifecycleOwner?.lifecycle?.removeObserver(this)
            currentLifecycleOwner = null
            activeAnalyzer = null
            _isReady.value = false

            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing CameraXControllerImpl", e)
        }
    }
}
