package com.localscan.domain.model

/**
 * Diagnostic metrics evaluating the visual and geometric quality of a document capture.
 */
data class QualityMetrics(
    val blurScore: Float = 0f,
    val glareScore: Float = 0f,
    val shadowScore: Float = 0f,
    val exposureScore: Float = 0f,
    val geometryScore: Float = 0f,
    val cropConfidence: Float = 0f,
    val cornerConfidence: Float = 0f,
    val overallScore: Float = 0f
)
