package com.yscanner.domain.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageRectTest {

    @Test
    fun `a fully inside rect is returned unchanged`() {
        val rect = ImageRect(10, 20, 30, 40)

        assertThat(rect.clampTo(100, 100)).isEqualTo(ImageRect(10, 20, 30, 40))
    }

    @Test
    fun `a rect overhanging the left and top edges clamps to zero`() {
        val rect = ImageRect(-5, -7, 30, 40)

        assertThat(rect.clampTo(100, 100)).isEqualTo(ImageRect(0, 0, 30, 40))
    }

    @Test
    fun `a rect overhanging the right and bottom edges clamps to width and height`() {
        val rect = ImageRect(10, 20, 150, 250)

        assertThat(rect.clampTo(100, 100)).isEqualTo(ImageRect(10, 20, 100, 100))
    }

    @Test
    fun `a rect entirely to the right of the image returns null`() {
        val rect = ImageRect(200, 0, 300, 100)

        assertThat(rect.clampTo(100, 100)).isNull()
    }

    @Test
    fun `a rect entirely below the image returns null`() {
        val rect = ImageRect(0, 200, 100, 300)

        assertThat(rect.clampTo(100, 100)).isNull()
    }

    @Test
    fun `a rect entirely above and left of the origin returns null`() {
        val rect = ImageRect(-100, -100, -50, -50)

        assertThat(rect.clampTo(100, 100)).isNull()
    }

    @Test
    fun `a rect touching the right and bottom edges exactly is kept`() {
        val rect = ImageRect(0, 0, 100, 100)

        assertThat(rect.clampTo(100, 100)).isEqualTo(ImageRect(0, 0, 100, 100))
    }

    @Test
    fun `zero image dimensions return null`() {
        val rect = ImageRect(0, 0, 10, 10)

        assertThat(rect.clampTo(0, 100)).isNull()
        assertThat(rect.clampTo(100, 0)).isNull()
    }

    @Test
    fun `negative image dimensions return null`() {
        val rect = ImageRect(0, 0, 10, 10)

        assertThat(rect.clampTo(-5, -5)).isNull()
    }

    @Test
    fun `a zero width rect returns null`() {
        val rect = ImageRect(10, 10, 10, 20)

        assertThat(rect.isEmpty).isTrue()
        assertThat(rect.clampTo(100, 100)).isNull()
    }

    @Test
    fun `a zero height rect returns null`() {
        val rect = ImageRect(10, 10, 20, 10)

        assertThat(rect.isEmpty).isTrue()
        assertThat(rect.clampTo(100, 100)).isNull()
    }

    @Test
    fun `centredOn with an even size produces a size square centred on the point`() {
        val rect = ImageRect.centredOn(50f, 60f, 4)

        assertThat(rect).isEqualTo(ImageRect(48, 58, 52, 62))
        assertThat(rect.width).isEqualTo(4)
        assertThat(rect.height).isEqualTo(4)
    }

    @Test
    fun `centredOn with an odd size stays within half a pixel of the centre`() {
        val rect = ImageRect.centredOn(50f, 60f, 5)

        assertThat(rect.width).isEqualTo(5)
        assertThat(rect.height).isEqualTo(5)
        val centreX = (rect.left + rect.right) / 2f
        val centreY = (rect.top + rect.bottom) / 2f
        assertThat(kotlin.math.abs(centreX - 50f)).isAtMost(0.5f)
        assertThat(kotlin.math.abs(centreY - 60f)).isAtMost(0.5f)
    }

    @Test
    fun `an inverted rect reports negative dimensions and is empty`() {
        val rect = ImageRect(20, 20, 10, 10)

        assertThat(rect.width).isEqualTo(-10)
        assertThat(rect.height).isEqualTo(-10)
        assertThat(rect.isEmpty).isTrue()
    }

    @Test
    fun `an inverted rect clamps to null rather than a negative region`() {
        val rect = ImageRect(20, 20, 10, 10)

        assertThat(rect.clampTo(100, 100)).isNull()
    }
}
