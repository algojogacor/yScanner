package com.yscanner.domain.model

/**
 * Represents a document scanning session containing an ordered collection of pages.
 */
data class ScanSession(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val pages: List<PageObject> = emptyList()
) {
    val pageCount: Int get() = pages.size
}
