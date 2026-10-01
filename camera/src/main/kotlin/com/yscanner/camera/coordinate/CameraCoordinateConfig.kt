package com.yscanner.camera.coordinate

import com.yscanner.domain.model.Quad

/**
 * PreviewView scale type for mapping between normalized scene and view pixels.
 */
enum class PreviewScaleType {
    /** Aspect fill: fills entire view, center cropped (no black bars). */
    FILL_CENTER,

    /** Aspect fit: preserves aspect ratio, entire scene visible (letterboxed/pillarboxed). */
    FIT_CENTER
}

/**
 * Configuration state representing hardware and pipeline dimensions for coordinate transformation.
 */
data class CameraCoordinateConfig(
    // 1. SENSOR
    val sensorWidth: Int = 4032,
    val sensorHeight: Int = 3024,
    val sensorRotationDegrees: Int = 90,

    // 2. IMAGE_ANALYSIS
    val analysisWidth: Int = 1280,
    val analysisHeight: Int = 720,
    val analysisRotationDegrees: Int = 90,
    val analysisCropLeft: Int = 0,
    val analysisCropTop: Int = 0,
    val analysisCropRight: Int = 1280,
    val analysisCropBottom: Int = 720,

    // 3. PREVIEW_VIEW
    val previewWidth: Int = 1080,
    val previewHeight: Int = 2400,
    val previewScaleType: PreviewScaleType = PreviewScaleType.FILL_CENTER,

    // 4. IMAGE_CAPTURE
    val captureWidth: Int = 4032,
    val captureHeight: Int = 3024,
    val captureRotationDegrees: Int = 90,

    // 5. PROCESSED
    val processedQuad: Quad? = null,
    val processedWidth: Int = 2480,
    val processedHeight: Int = 3508,

    // 6. PDF
    val pdfPageWidthPt: Float = 595.28f,
    val pdfPageHeightPt: Float = 841.89f,
    val pdfMarginLeftPt: Float = 0f,
    val pdfMarginTopPt: Float = 0f,
    val pdfMarginRightPt: Float = 0f,
    val pdfMarginBottomPt: Float = 0f
)
