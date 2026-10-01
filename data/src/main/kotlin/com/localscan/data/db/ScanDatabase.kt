package com.localscan.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.localscan.data.db.converter.EnumConverters
import com.localscan.data.db.converter.QuadConverter
import com.localscan.data.db.dao.PageDao
import com.localscan.data.db.dao.SessionDao
import com.localscan.data.entity.PageEntity
import com.localscan.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        PageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    QuadConverter::class,
    EnumConverters::class
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun pageDao(): PageDao

    companion object {
        const val DATABASE_NAME = "localscan_database.db"
    }
}
