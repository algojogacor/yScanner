package com.localscan.processing

import android.graphics.Bitmap
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.Quad

interface ImageProcessor {
    suspend fun rectify(sourceUri: String, quad: Quad): Bitmap
    suspend fun enhance(bitmap: Bitmap, mode: EnhancementMode): Bitmap
    suspend fun dewarpBookSpread(sourceUri: String): Pair<Bitmap, Bitmap>
}
