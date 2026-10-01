package com.yscanner.camera.capture

import androidx.camera.core.ImageCapture
import java.io.File

/**
 * Strategy interface for taking captures and writing directly to disk storage.
 */
interface CaptureManager {
    /**
     * Executes file-backed capture using the provided [ImageCapture] use case.
     * Suspends until the file is written and metadata extracted, or returns a failure.
     */
    suspend fun captureToFile(
        imageCapture: ImageCapture,
        outputFile: File
    ): Result<CaptureResult>
}
