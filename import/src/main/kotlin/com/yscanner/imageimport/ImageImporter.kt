package com.yscanner.imageimport

import android.net.Uri

interface ImageImporter {
    suspend fun importFromUri(uri: Uri): Result<String>
}
