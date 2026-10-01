package com.yscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yscanner.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY modifiedAt DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY modifiedAt DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
