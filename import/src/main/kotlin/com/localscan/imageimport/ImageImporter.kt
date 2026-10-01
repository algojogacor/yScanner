package com.localscan.imageimport

import android.net.Uri

interface ImageImporter {
    suspend fun importFromUri(uri: Uri): Result<String>
}
