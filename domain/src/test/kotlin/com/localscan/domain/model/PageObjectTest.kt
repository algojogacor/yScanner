package com.localscan.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageObjectTest {

    private val sampleQuad = Quad(
        topLeft = Corner(0f, 0f),
        topRight = Corner(100f, 0f),
        bottomRight = Corner(100f, 150f),
        bottomLeft = Corner(0f, 150f)
    )

    @Test
    fun `activeQuad defaults to detectedQuad when userQuad is null`() {
        val page = PageObject(
            id = "p1",
            sessionId = "s1",
            pageIndex = 0,
            sourceImageUri = "file:///tmp/img1.jpg",
            detectedQuad = sampleQuad
        )
        assertThat(page.activeQuad).isEqualTo(sampleQuad)
    }

    @Test
    fun `activeQuad prefers userQuad when present`() {
        val userModifiedQuad = sampleQuad.copy(
            topLeft = Corner(10f, 10f)
        )
        val page = PageObject(
            id = "p1",
            sessionId = "s1",
            pageIndex = 0,
            sourceImageUri = "file:///tmp/img1.jpg",
            detectedQuad = sampleQuad,
            userQuad = userModifiedQuad
        )
        assertThat(page.activeQuad).isEqualTo(userModifiedQuad)
    }

    @Test
    fun `PageSize constants have correct point dimensions`() {
        assertThat(PageSize.A4.widthPt).isWithin(0.01f).of(595.28f)
        assertThat(PageSize.A4.heightPt).isWithin(0.01f).of(841.89f)
        assertThat(PageSize.LETTER.widthPt).isWithin(0.01f).of(612.00f)
        assertThat(PageSize.LETTER.heightPt).isWithin(0.01f).of(792.00f)
    }

    @Test
    fun `QualityProfile compression values adhere to spec`() {
        assertThat(QualityProfile.HIGH.compressionQuality).isEqualTo(90)
        assertThat(QualityProfile.HIGH.maxDimension).isNull()

        assertThat(QualityProfile.BALANCED.compressionQuality).isEqualTo(80)
        assertThat(QualityProfile.BALANCED.maxDimension).isEqualTo(2048)

        assertThat(QualityProfile.SMALL.compressionQuality).isEqualTo(65)
        assertThat(QualityProfile.SMALL.maxDimension).isEqualTo(1280)
    }
}
