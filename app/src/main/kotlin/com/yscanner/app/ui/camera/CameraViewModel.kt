package com.yscanner.app.ui.camera

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yscanner.camera.CameraController
import com.yscanner.camera.CameraXControllerImpl
import com.yscanner.domain.model.FlashMode
import com.yscanner.domain.model.PointF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel(
    private val cameraController: CameraController = CameraXControllerImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Atomic guard prevents concurrent shutter clicks from triggering double-capture. */
    private val _capturing = AtomicBoolean(false)

    init {
        // Observe CameraController states
        viewModelScope.launch {
            cameraController.isReady.collect { ready ->
                _uiState.update { it.copy(isReady = ready) }
            }
        }
        viewModelScope.launch {
            cameraController.flashMode.collect { mode ->
                _uiState.update { it.copy(flashMode = mode) }
            }
        }
        viewModelScope.launch {
            cameraController.hasFlashUnit.collect { hasFlash ->
                _uiState.update { it.copy(hasFlashUnit = hasFlash) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
    }

    fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            try {
                cameraController.initialize(context, lifecycleOwner, previewView)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to initialize camera: ${e.message}") }
            }
        }
    }

    fun onPreviewTapped(point: PointF) {
        cameraController.focusOnPoint(point)
        _uiState.update {
            it.copy(
                lastFocusPoint = point,
                focusTriggerKey = System.currentTimeMillis()
            )
        }
    }

    fun onFlashModeChanged(mode: FlashMode) {
        cameraController.setFlashMode(mode)
    }

    fun capturePhoto(cacheDir: File) {
        // Atomic CAS: only one coroutine proceeds if _capturing was false
        if (!_uiState.value.isReady || !_capturing.compareAndSet(false, true)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
            try {
                val captureDir = File(cacheDir, "captures").apply { mkdirs() }
                val outputFile = File(captureDir, "scan_${System.currentTimeMillis()}.jpg")

                val result = cameraController.captureImage(outputFile)
                result.onSuccess { captureResult ->
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastCaptureResult = captureResult
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            errorMessage = "Capture failed: ${error.message}"
                        )
                    }
                }
            } finally {
                _capturing.set(false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.release()
    }
}
