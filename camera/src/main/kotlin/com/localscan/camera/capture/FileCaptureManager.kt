package com.localscan.camera.capture

import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Strategy interface for reading image dimensions and EXIF metadata without full bitmap decode.
 */
interface ImageMetadataReader {
    fun read(file: File): RawMetadata

    data class RawMetadata(
        val rawWidth: Int,
        val rawHeight: Int,
        val rotationDegrees: Int
    )
}

/**
 * Default implementation of [ImageMetadataReader] using BitmapFactory bounds decode and ExifInterface.
 */
class DefaultImageMetadataReader : ImageMetadataReader {
    override fun read(file: File): ImageMetadataReader.RawMetadata {
        // 1. Read bounds only (0 MB RAM allocation)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight

        // 2. Read EXIF rotation metadata
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        return ImageMetadataReader.RawMetadata(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            rotationDegrees = rotationDegrees
        )
    }
}

/**
 * Concrete [CaptureManager] that saves captures directly to disk.
 *
 * Adheres strictly to AGENTS.md §10:
 * - Direct file write via CameraX [ImageCapture.OutputFileOptions].
 * - Zero full-resolution Bitmap allocations in memory.
 * - Dimensions are parsed using BitmapFactory.Options.inJustDecodeBounds = true.
 * - Orientation is parsed using ExifInterface from the saved file header.
 */
class FileCaptureManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val metadataReader: ImageMetadataReader = DefaultImageMetadataReader()
) : CaptureManager {

    override suspend fun captureToFile(
        imageCapture: ImageCapture,
        outputFile: File
    ): Result<CaptureResult> = withContext(ioDispatcher) {
        outputFile.parentFile?.mkdirs()

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        suspendCancellableCoroutine { continuation ->
            val callbackExecutor = Executor { command -> command.run() }

            imageCapture.takePicture(
                outputOptions,
                callbackExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            val result = extractMetadata(outputFile)
                            continuation.resume(Result.success(result))
                        } catch (e: Exception) {
                            continuation.resume(Result.failure(e))
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            )
        }
    }

    /**
     * Extracts dimensions and EXIF orientation directly from file header with ZERO pixel buffer allocations.
     *
     * @throws IllegalStateException if the file is unreadable or BitmapFactory returns invalid bounds.
     */
    internal fun extractMetadata(file: File): CaptureResult {
        val meta = metadataReader.read(file)

        check(meta.rawWidth > 0 && meta.rawHeight > 0) {
            "FileCaptureManager: BitmapFactory returned invalid image bounds " +
                "(${meta.rawWidth}×${meta.rawHeight}) for file '${file.name}'. " +
                "The file may be corrupt or unreadable."
        }

        // Normalize width & height for consumer upright orientation
        val (finalWidth, finalHeight) = if (meta.rotationDegrees == 90 || meta.rotationDegrees == 270) {
            meta.rawHeight to meta.rawWidth
        } else {
            meta.rawWidth to meta.rawHeight
        }

        return CaptureResult(
            file = file,
            rotationDegrees = meta.rotationDegrees,
            width = finalWidth,
            height = finalHeight
        )
    }
}
