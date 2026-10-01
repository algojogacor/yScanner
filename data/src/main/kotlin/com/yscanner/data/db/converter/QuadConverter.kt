package com.yscanner.data.db.converter

import androidx.room.TypeConverter
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.Quad

class QuadConverter {
    @TypeConverter
    fun fromQuad(quad: Quad?): String? {
        if (quad == null) return null
        return buildString {
            append(quad.topLeft.x).append(',').append(quad.topLeft.y).append(',').append(quad.topLeft.confidence).append(',')
            append(quad.topRight.x).append(',').append(quad.topRight.y).append(',').append(quad.topRight.confidence).append(',')
            append(quad.bottomRight.x).append(',').append(quad.bottomRight.y).append(',').append(quad.bottomRight.confidence).append(',')
            append(quad.bottomLeft.x).append(',').append(quad.bottomLeft.y).append(',').append(quad.bottomLeft.confidence)
        }
    }

    @TypeConverter
    fun toQuad(value: String?): Quad? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(',')
        if (parts.size < 8) return null

        val tlC = if (parts.size >= 12) parts[2].toFloatOrNull() ?: 1.0f else 1.0f
        val trC = if (parts.size >= 12) parts[5].toFloatOrNull() ?: 1.0f else 1.0f
        val brC = if (parts.size >= 12) parts[8].toFloatOrNull() ?: 1.0f else 1.0f
        val blC = if (parts.size >= 12) parts[11].toFloatOrNull() ?: 1.0f else 1.0f

        return if (parts.size >= 12) {
            Quad(
                topLeft = Corner(parts[0].toFloat(), parts[1].toFloat(), tlC),
                topRight = Corner(parts[3].toFloat(), parts[4].toFloat(), trC),
                bottomRight = Corner(parts[6].toFloat(), parts[7].toFloat(), brC),
                bottomLeft = Corner(parts[9].toFloat(), parts[10].toFloat(), blC)
            )
        } else {
            Quad(
                topLeft = Corner(parts[0].toFloat(), parts[1].toFloat(), 1f),
                topRight = Corner(parts[2].toFloat(), parts[3].toFloat(), 1f),
                bottomRight = Corner(parts[4].toFloat(), parts[5].toFloat(), 1f),
                bottomLeft = Corner(parts[6].toFloat(), parts[7].toFloat(), 1f)
            )
        }
    }
}
