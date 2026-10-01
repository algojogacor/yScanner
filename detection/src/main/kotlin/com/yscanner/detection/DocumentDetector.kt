package com.yscanner.detection

import androidx.camera.core.ImageProxy
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad

data class DocumentCandidate(
    val id: String,
    val quad: Quad,
    val confidence: Float,
    val area: Float
)

data class TrackedDocument(
    val id: String,
    val quad: Quad,
    val confidence: Float,
    val isStable: Boolean
)

interface DocumentDetector {
    suspend fun detect(imageProxy: ImageProxy): List<DocumentCandidate>
}

interface TemporalTracker {
    fun update(candidates: List<DocumentCandidate>, tapPrior: PointF?): TrackedDocument?
    fun reset()
}
