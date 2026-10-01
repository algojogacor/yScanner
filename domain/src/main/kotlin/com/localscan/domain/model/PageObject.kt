package com.localscan.domain.model

/**
 * Authoritative domain model representing a single scanned page.
 */
data class PageObject(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val sourceImageUri: String,
    val detectedQuad: Quad,
    val userQuad: Quad? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.NATURAL,
    val isDewarped: Boolean = false,
    val qualityMetrics: QualityMetrics? = null
) {
    val activeQuad: Quad get() = userQuad ?: detectedQuad
}
