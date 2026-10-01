package com.localscan.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuadTest {

    @Test
    fun `isConvex returns true for regular rectangle`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(100f, 200f),
            bottomLeft = Corner(0f, 200f)
        )
        assertThat(quad.isConvex()).isTrue()
    }

    @Test
    fun `isConvex returns true for rotated trapezoid`() {
        val quad = Quad(
            topLeft = Corner(20f, 10f),
            topRight = Corner(80f, 15f),
            bottomRight = Corner(95f, 190f),
            bottomLeft = Corner(5f, 185f)
        )
        assertThat(quad.isConvex()).isTrue()
    }

    @Test
    fun `isConvex returns false for concave dart shape`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(50f, 50f), // indented inward corner
            bottomLeft = Corner(0f, 100f)
        )
        assertThat(quad.isConvex()).isFalse()
    }

    @Test
    fun `isConvex returns false for self-intersecting bowtie quad`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 100f), // crossed edges
            bottomRight = Corner(100f, 0f),
            bottomLeft = Corner(0f, 100f)
        )
        assertThat(quad.isConvex()).isFalse()
    }

    @Test
    fun `area computes exact rectangular area`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(100f, 200f),
            bottomLeft = Corner(0f, 200f)
        )
        assertThat(quad.area()).isEqualTo(20000.0f)
    }

    @Test
    fun `area computes trapezoidal area accurately`() {
        // Trapezoid with top edge 60, bottom edge 100, height 100
        val quad = Quad(
            topLeft = Corner(20f, 0f),
            topRight = Corner(80f, 0f),
            bottomRight = Corner(100f, 100f),
            bottomLeft = Corner(0f, 100f)
        )
        // Area = 0.5 * (60 + 100) * 100 = 8000.0f
        assertThat(quad.area()).isEqualTo(8000.0f)
    }

    @Test
    fun `toArray and fromArray serialize and deserialize symmetrically`() {
        val original = Quad(
            topLeft = Corner(12.5f, 34.2f),
            topRight = Corner(56.7f, 78.1f),
            bottomRight = Corner(90.3f, 12.8f),
            bottomLeft = Corner(34.9f, 98.4f)
        )
        val array = original.toArray()
        assertThat(array.size).isEqualTo(8)

        val restored = Quad.fromArray(array)
        assertThat(restored.topLeft.x).isEqualTo(original.topLeft.x)
        assertThat(restored.topLeft.y).isEqualTo(original.topLeft.y)
        assertThat(restored.topRight.x).isEqualTo(original.topRight.x)
        assertThat(restored.bottomRight.x).isEqualTo(original.bottomRight.x)
        assertThat(restored.bottomLeft.x).isEqualTo(original.bottomLeft.x)
    }
}
