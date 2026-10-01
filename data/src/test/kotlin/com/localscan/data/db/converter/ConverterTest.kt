package com.localscan.data.db.converter

import com.google.common.truth.Truth.assertThat
import com.localscan.domain.model.Corner
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.Quad
import org.junit.Test

class ConverterTest {

    private val quadConverter = QuadConverter()
    private val enumConverters = EnumConverters()

    @Test
    fun `QuadConverter roundtrip serialization preserves values`() {
        val original = Quad(
            topLeft = Corner(1.1f, 2.2f, 0.95f),
            topRight = Corner(3.3f, 4.4f, 0.90f),
            bottomRight = Corner(5.5f, 6.6f, 0.85f),
            bottomLeft = Corner(7.7f, 8.8f, 0.80f)
        )
        val serialized = quadConverter.fromQuad(original)
        assertThat(serialized).isNotNull()

        val deserialized = quadConverter.toQuad(serialized)
        assertThat(deserialized).isEqualTo(original)
    }

    @Test
    fun `QuadConverter gracefully handles null and malformed strings`() {
        assertThat(quadConverter.fromQuad(null)).isNull()
        assertThat(quadConverter.toQuad(null)).isNull()
        assertThat(quadConverter.toQuad("")).isNull()
        assertThat(quadConverter.toQuad("1.0,2.0")).isNull() // Less than 8 parts
    }

    @Test
    fun `EnumConverters correctly serializes and deserializes EnhancementMode`() {
        val mode = EnhancementMode.CLEAN
        val name = enumConverters.fromEnhancementMode(mode)
        assertThat(name).isEqualTo("CLEAN")
        assertThat(enumConverters.toEnhancementMode(name)).isEqualTo(EnhancementMode.CLEAN)
        assertThat(enumConverters.toEnhancementMode("INVALID_NAME")).isEqualTo(EnhancementMode.NATURAL)
    }
}
