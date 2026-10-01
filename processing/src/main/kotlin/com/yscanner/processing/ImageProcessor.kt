package com.yscanner.processing

import android.graphics.Bitmap
import com.yscanner.domain.model.EnhancementMode
import com.yscanner.domain.model.Quad

interface ImageProcessor {
    suspend fun rectify(sourceUri: String, quad: Quad): Bitmap
    suspend fun enhance(bitmap: Bitmap, mode: EnhancementMode): Bitmap
    suspend fun dewarpBookSpread(sourceUri: String): Pair<Bitmap, Bitmap>
}
