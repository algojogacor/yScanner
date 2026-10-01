package com.yscanner.domain.model

enum class EnhancementMode {
    ORIGINAL,
    NATURAL,
    CLEAN
}

enum class FlashMode {
    OFF,
    ON,
    TORCH
}

enum class CaptureMode {
    ONE_PAGE,
    TWO_PAGE
}

enum class PageSize(val widthPt: Float, val heightPt: Float) {
    A4(595.28f, 841.89f),
    AUTO(0f, 0f),
    A5(419.53f, 595.28f),
    B5(498.90f, 708.66f),
    LETTER(612.00f, 792.00f),
    ORIGINAL_RATIO(0f, 0f)
}

enum class QualityProfile(
    val compressionQuality: Int,
    val maxDimension: Int?
) {
    HIGH(compressionQuality = 90, maxDimension = null),
    BALANCED(compressionQuality = 80, maxDimension = 2048),
    SMALL(compressionQuality = 65, maxDimension = 1280)
}
