package com.localscan.data.db.converter

import androidx.room.TypeConverter
import com.localscan.domain.model.CaptureMode
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.FlashMode
import com.localscan.domain.model.PageSize
import com.localscan.domain.model.QualityProfile

class EnumConverters {
    @TypeConverter
    fun fromEnhancementMode(mode: EnhancementMode?): String? = mode?.name

    @TypeConverter
    fun toEnhancementMode(value: String?): EnhancementMode? =
        value?.let { runCatching { EnhancementMode.valueOf(it) }.getOrDefault(EnhancementMode.NATURAL) }

    @TypeConverter
    fun fromFlashMode(mode: FlashMode?): String? = mode?.name

    @TypeConverter
    fun toFlashMode(value: String?): FlashMode? =
        value?.let { runCatching { FlashMode.valueOf(it) }.getOrDefault(FlashMode.OFF) }

    @TypeConverter
    fun fromCaptureMode(mode: CaptureMode?): String? = mode?.name

    @TypeConverter
    fun toCaptureMode(value: String?): CaptureMode? =
        value?.let { runCatching { CaptureMode.valueOf(it) }.getOrDefault(CaptureMode.ONE_PAGE) }

    @TypeConverter
    fun fromPageSize(size: PageSize?): String? = size?.name

    @TypeConverter
    fun toPageSize(value: String?): PageSize? =
        value?.let { runCatching { PageSize.valueOf(it) }.getOrDefault(PageSize.A4) }

    @TypeConverter
    fun fromQualityProfile(profile: QualityProfile?): String? = profile?.name

    @TypeConverter
    fun toQualityProfile(value: String?): QualityProfile? =
        value?.let { runCatching { QualityProfile.valueOf(it) }.getOrDefault(QualityProfile.BALANCED) }
}
