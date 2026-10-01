package com.localscan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.localscan.data.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: String): PageEntity?

    @Query("SELECT * FROM pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun observePagesForSession(sessionId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPagesForSession(sessionId: String): List<PageEntity>

    @Query("SELECT COUNT(*) FROM pages WHERE sessionId = :sessionId")
    suspend fun getPageCountForSession(sessionId: String): Int

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: String)

    @Query("DELETE FROM pages WHERE sessionId = :sessionId")
    suspend fun deletePagesForSession(sessionId: String)

    @Transaction
    suspend fun updatePageIndices(reorderedPages: List<PageEntity>) {
        for (page in reorderedPages) {
            updatePage(page)
        }
    }
}
