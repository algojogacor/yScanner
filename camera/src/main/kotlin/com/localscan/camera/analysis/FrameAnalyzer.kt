package com.localscan.camera.analysis

import androidx.camera.core.ImageProxy

/**
 * Consumer interface for camera analysis frames (e.g. ML Document Detector).
 *
 * Contract:
 * - Executed on a background worker thread ([com.localscan.camera.executor.CameraExecutor]).
 * - Must NOT retain [imageProxy] references beyond the invocation of [analyze].
 * - The calling infrastructure guarantees that [ImageProxy.close] is executed in a finally block.
 */
fun interface FrameAnalyzer {
    fun analyze(imageProxy: ImageProxy)
}
