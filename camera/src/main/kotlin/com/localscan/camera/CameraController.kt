package com.localscan.camera

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.localscan.camera.analysis.FrameAnalyzer
import com.localscan.camera.capture.CaptureResult
import com.localscan.domain.model.FlashMode
import com.localscan.domain.model.PointF
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Primary camera acquisition interface for LocalScan.
 * Fully decoupled from UI implementation details.
 */
interface CameraController {
    /** Current active flash mode (OFF, ON, TORCH). */
    val flashMode: StateFlow<FlashMode>

    /** Indicates whether CameraProvider is bound and actively streaming. */
    val isReady: StateFlow<Boolean>

    /** Indicates whether the current camera hardware possesses a physical flash unit. */
    val hasFlashUnit: StateFlow<Boolean>

    /**
     * Initializes CameraX, requests [androidx.camera.lifecycle.ProcessCameraProvider], configures use cases,
     * and binds them to the provided [lifecycleOwner] and [previewView].
     */
    suspend fun initialize(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    )

    /**
     * Sets or switches the active flash mode.
     */
    fun setFlashMode(mode: FlashMode)

    /**
     * Triggers autofocus and auto-exposure metering on a specific touch coordinate within [PreviewView].
     * Uses 3-second auto-cancel to return to continuous autofocus.
     */
    fun focusOnPoint(previewPoint: PointF)

    /**
     * Registers or unregisters an [analyzer] for real-time document detection frames.
     */
    fun setFrameAnalyzer(analyzer: FrameAnalyzer?)

    /**
     * Captures a full-resolution still image directly to [outputFile].
     * Never retains full-resolution Bitmaps in memory.
     */
    suspend fun captureImage(outputFile: File): Result<CaptureResult>

    /**
     * Unbinds use cases, shuts down background executors, and resets state.
     */
    fun release()
}
