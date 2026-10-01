package com.yscanner.camera.coordinate

/**
 * The 7 coordinate spaces defined in yScanner architecture (PRD §10-11 & ARCHITECTURE.md §16-19).
 */
enum class CoordinateSpace {
    /** Physical active camera sensor array (hardware native, landscape). */
    SENSOR,

    /** Low-resolution ML frame buffer from CameraX ImageAnalysis (sensor native + rotation). */
    IMAGE_ANALYSIS,

    /** Upright UI viewfinder pixel dimensions of PreviewView. */
    PREVIEW_VIEW,

    /** Canonical resolution-agnostic unit square [0.0, 1.0] x [0.0, 1.0] upright visual field. */
    NORMALIZED,

    /** High-resolution still photo asset from CameraX ImageCapture (buffer native + EXIF rotation). */
    IMAGE_CAPTURE,

    /** Rectified, perspective-corrected document page image. */
    PROCESSED,

    /** Final PDF canvas page coordinate space in typographic points (1/72 inch). */
    PDF
}
