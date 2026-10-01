package com.yscanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yscanner.data.db.converter.EnumConverters
import com.yscanner.data.db.converter.QuadConverter
import com.yscanner.data.db.dao.PageDao
import com.yscanner.data.db.dao.SessionDao
import com.yscanner.data.entity.PageEntity
import com.yscanner.data.entity.SessionEntity

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
        const val DATABASE_NAME = "yscanner_database.db"
    }
}
