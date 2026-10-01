package com.yscanner.data.db.converter

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.Quad
import org.junit.Assert.assertThrows
import org.junit.Test

class QuadConverterAdversarialTest {

    private val converter = QuadConverter()

    // -------------------------------------------------------------------------
    // 1. Corrupted strings: empty, whitespace, truncated, non-numeric
    // -------------------------------------------------------------------------

    @Test
    fun `toQuad returns null for empty or whitespace strings`() {
        assertThat(converter.toQuad("")).isNull()
        assertThat(converter.toQuad("   ")).isNull()
        assertThat(converter.toQuad("\t\n")).isNull()
    }

    @Test
    fun `toQuad returns null when string has fewer than 8 parts`() {
        assertThat(converter.toQuad("1.0,2.0,3.0,4.0,5.0,6.0,7.0")).isNull()
        assertThat(converter.toQuad("only_one_token")).isNull()
        assertThat(converter.toQuad(",,,,")).isNull()
    }

    @Test
    fun `toQuad throws NumberFormatException when 8-part string has non-numeric tokens`() {
        // Empirically demonstrates vulnerability: converter does not catch NumberFormatException
        // on malformed coordinates, which will crash Room database cursor queries.
        assertThrows(NumberFormatException::class.java) {
            converter.toQuad("invalid,float,tokens,here,for,eight,coordinate,values")
        }
    }

    @Test
    fun `toQuad throws NumberFormatException when 12-part string has non-numeric coordinates`() {
        val corrupted12 = "1.0,corrupted,1.0,2.0,3.0,1.0,4.0,5.0,1.0,6.0,7.0,1.0"
        assertThrows(NumberFormatException::class.java) {
            converter.toQuad(corrupted12)
        }
    }

    @Test
    fun `toQuad throws NumberFormatException on empty token within comma-separated values`() {
        val emptyTokenString = "1.0,2.0,,4.0,5.0,6.0,7.0,8.0"
        assertThrows(NumberFormatException::class.java) {
            converter.toQuad(emptyTokenString)
        }
    }

    @Test
    fun `toQuad silently causes field mismatch when 12-part format is truncated to 9 parts`() {
        // In 12-part format: [tl.x, tl.y, tl.conf, tr.x, tr.y, tr.conf, br.x, br.y, br.conf, bl.x, bl.y, bl.conf]
        // If truncated to 9 parts, parts.size < 12 fallback treats parts as 8-part [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
        // Consequently, tl.conf (parts[2] = 0.85) is silently misassigned to tr.x!
        val truncated12Part = "10.0,20.0,0.85,30.0,40.0,0.90,50.0,60.0,0.95"
        val result = converter.toQuad(truncated12Part)

        assertThat(result).isNotNull()
        // tr.x is mistakenly set to 0.85 (which was tl.confidence) instead of 30.0
        assertThat(result!!.topRight.x).isEqualTo(0.85f)
        assertThat(result.topRight.y).isEqualTo(30.0f)
    }

    // -------------------------------------------------------------------------
    // 2. NaN and Infinity handling
    // -------------------------------------------------------------------------

    @Test
    fun `roundtrip preserves NaN coordinates but produces non-convex quad with NaN area`() {
        val nanQuad = Quad(
            topLeft = Corner(Float.NaN, Float.NaN, 1.0f),
            topRight = Corner(Float.NaN, Float.NaN, 1.0f),
            bottomRight = Corner(Float.NaN, Float.NaN, 1.0f),
            bottomLeft = Corner(Float.NaN, Float.NaN, 1.0f)
        )
        val serialized = converter.fromQuad(nanQuad)
        assertThat(serialized).isEqualTo("NaN,NaN,1.0,NaN,NaN,1.0,NaN,NaN,1.0,NaN,NaN,1.0")

        val deserialized = converter.toQuad(serialized)
        assertThat(deserialized).isNotNull()
        assertThat(deserialized!!.topLeft.x.isNaN()).isTrue()
        assertThat(deserialized.topLeft.y.isNaN()).isTrue()

        // Downstream domain impacts:
        assertThat(deserialized.isConvex()).isFalse()
        assertThat(deserialized.area().isNaN()).isTrue()
    }

    @Test
    fun `roundtrip preserves Infinity coordinates but produces infinite area`() {
        val infQuad = Quad(
            topLeft = Corner(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 1.0f),
            topRight = Corner(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 1.0f),
            bottomRight = Corner(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 1.0f),
            bottomLeft = Corner(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 1.0f)
        )
        val serialized = converter.fromQuad(infQuad)
        assertThat(serialized).isEqualTo("Infinity,Infinity,1.0,Infinity,Infinity,1.0,Infinity,Infinity,1.0,Infinity,Infinity,1.0")

        val deserialized = converter.toQuad(serialized)
        assertThat(deserialized).isNotNull()
        assertThat(deserialized!!.topLeft.x.isInfinite()).isTrue()

        // Downstream domain impacts:
        // In IEEE 754 float math, Infinity * Infinity - Infinity * Infinity evaluates to NaN (indeterminate form)
        assertThat(deserialized.isConvex()).isFalse()
        assertThat(deserialized.area().isNaN()).isTrue()
    }

    // -------------------------------------------------------------------------
    // 3. Extreme floating point values (1e-6, 1e6, Float MIN_VALUE, MAX_VALUE)
    // -------------------------------------------------------------------------

    @Test
    fun `roundtrip preserves small floating point values 1e-6`() {
        val smallQuad = Quad(
            topLeft = Corner(1e-6f, 1e-6f, 0.95f),
            topRight = Corner(2e-6f, 1e-6f, 0.90f),
            bottomRight = Corner(2e-6f, 2e-6f, 0.85f),
            bottomLeft = Corner(1e-6f, 2e-6f, 0.80f)
        )
        val serialized = converter.fromQuad(smallQuad)
        assertThat(serialized).isNotNull()
        // Serialized representation uses scientific notation "1.0E-6"
        assertThat(serialized).contains("1.0E-6")

        val deserialized = converter.toQuad(serialized)
        assertThat(deserialized).isEqualTo(smallQuad)

        // Subtle vulnerability: at 1e-6 scale, cross product is ~1e-12 < epsilon(1e-4f),
        // causing a geometrically valid square to be flagged as non-convex!
        assertThat(smallQuad.isConvex()).isFalse()
    }

    @Test
    fun `roundtrip preserves large floating point values 1e6`() {
        val largeQuad = Quad(
            topLeft = Corner(1e6f, 1e6f, 0.95f),
            topRight = Corner(2e6f, 1e6f, 0.90f),
            bottomRight = Corner(2e6f, 2e6f, 0.85f),
            bottomLeft = Corner(1e6f, 2e6f, 0.80f)
        )
        val serialized = converter.fromQuad(largeQuad)
        assertThat(serialized).isNotNull()

        val deserialized = converter.toQuad(serialized)
        assertThat(deserialized).isEqualTo(largeQuad)
        assertThat(deserialized!!.isConvex()).isTrue()
        assertThat(deserialized.area()).isWithin(1e7f).of(1e12f)
    }

    @Test
    fun `roundtrip preserves Float MIN_VALUE and MAX_VALUE boundary limits`() {
        val boundaryQuad = Quad(
            topLeft = Corner(Float.MIN_VALUE, Float.MIN_VALUE, 1.0f),
            topRight = Corner(Float.MAX_VALUE, Float.MIN_VALUE, 1.0f),
            bottomRight = Corner(Float.MAX_VALUE, Float.MAX_VALUE, 1.0f),
            bottomLeft = Corner(Float.MIN_VALUE, Float.MAX_VALUE, 1.0f)
        )
        val serialized = converter.fromQuad(boundaryQuad)
        assertThat(serialized).isNotNull()

        val deserialized = converter.toQuad(serialized)
        assertThat(deserialized).isEqualTo(boundaryQuad)

        // Float multiplication of Float.MAX_VALUE causes area to overflow to Infinity
        assertThat(deserialized!!.area().isInfinite()).isTrue()
    }
}
