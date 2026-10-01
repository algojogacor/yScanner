package com.localscan.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.PageObject
import com.localscan.domain.model.Quad
import com.localscan.domain.model.QualityMetrics

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "pageIndex"])
    ]
)
data class PageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val sourceImageUri: String,
    val detectedQuad: Quad,
    val userQuad: Quad? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.NATURAL,
    val isDewarped: Boolean = false,
    val blurScore: Float? = null,
    val glareScore: Float? = null,
    val shadowScore: Float? = null,
    val exposureScore: Float? = null,
    val geometryScore: Float? = null,
    val cropConfidence: Float? = null,
    val cornerConfidence: Float? = null,
    val overallScore: Float? = null
) {
    fun toDomain(): PageObject = PageObject(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        sourceImageUri = sourceImageUri,
        detectedQuad = detectedQuad,
        userQuad = userQuad,
        rotationDegrees = rotationDegrees,
        enhancementMode = enhancementMode,
        isDewarped = isDewarped,
        qualityMetrics = if (overallScore != null || blurScore != null) {
            QualityMetrics(
                blurScore = blurScore ?: 0f,
                glareScore = glareScore ?: 0f,
                shadowScore = shadowScore ?: 0f,
                exposureScore = exposureScore ?: 0f,
                geometryScore = geometryScore ?: 0f,
                cropConfidence = cropConfidence ?: 0f,
                cornerConfidence = cornerConfidence ?: 0f,
                overallScore = overallScore ?: 0f
            )
        } else null
    )

    companion object {
        fun fromDomain(page: PageObject): PageEntity = PageEntity(
            id = page.id,
            sessionId = page.sessionId,
            pageIndex = page.pageIndex,
            sourceImageUri = page.sourceImageUri,
            detectedQuad = page.detectedQuad,
            userQuad = page.userQuad,
            rotationDegrees = page.rotationDegrees,
            enhancementMode = page.enhancementMode,
            isDewarped = page.isDewarped,
            blurScore = page.qualityMetrics?.blurScore,
            glareScore = page.qualityMetrics?.glareScore,
            shadowScore = page.qualityMetrics?.shadowScore,
            exposureScore = page.qualityMetrics?.exposureScore,
            geometryScore = page.qualityMetrics?.geometryScore,
            cropConfidence = page.qualityMetrics?.cropConfidence,
            cornerConfidence = page.qualityMetrics?.cornerConfidence,
            overallScore = page.qualityMetrics?.overallScore
        )
    }
}
