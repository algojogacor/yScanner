package com.localscan.pdf

import com.localscan.domain.model.PageObject
import com.localscan.domain.model.PageSize
import com.localscan.domain.model.QualityProfile
import java.io.File

interface PdfRenderer {
    suspend fun render(
        pages: List<PageObject>,
        outputFile: File,
        pageSize: PageSize,
        qualityProfile: QualityProfile,
        onProgress: (Int, Int) -> Unit
    ): Long

    fun estimateSize(pages: List<PageObject>, pageSize: PageSize, qualityProfile: QualityProfile): Long
}
