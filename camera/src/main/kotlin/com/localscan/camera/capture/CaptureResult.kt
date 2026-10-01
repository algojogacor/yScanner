package com.localscan.camera.capture

import java.io.File

/**
 * Immutable metadata result of an image capture.
 * Guaranteed to be backed by a persisted file on disk without retaining full-res Bitmaps in RAM.
 *
 * @property file Physical file on disk containing captured JPEG/HEIC asset.
 * @property rotationDegrees EXIF orientation rotation (0, 90, 180, 270) required to display upright.
 * @property width Physical pixel width after applying EXIF rotation.
 * @property height Physical pixel height after applying EXIF rotation.
 */
data class CaptureResult(
    val file: File,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int
)
