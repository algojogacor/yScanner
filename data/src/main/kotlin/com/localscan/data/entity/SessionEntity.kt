package com.localscan.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localscan.domain.model.PageObject
import com.localscan.domain.model.ScanSession

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toDomain(pages: List<PageObject> = emptyList()): ScanSession = ScanSession(
        id = id,
        name = name,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        pages = pages
    )

    companion object {
        fun fromDomain(session: ScanSession): SessionEntity = SessionEntity(
            id = session.id,
            name = session.name,
            createdAt = session.createdAt,
            modifiedAt = session.modifiedAt
        )
    }
}
