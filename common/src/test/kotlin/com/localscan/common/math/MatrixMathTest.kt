package com.localscan.common.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatrixMathTest {

    @Test
    fun `identity matrix maps point to exact coordinates`() {
        val identity = MatrixMath.identity()
        val (x, y) = MatrixMath.mapPoint(identity, 42.5f, 108.2f)
        assertThat(x).isWithin(1e-5f).of(42.5f)
        assertThat(y).isWithin(1e-5f).of(108.2f)
    }

    @Test
    fun `translation shifts coordinates by specified offsets`() {
        val translation = MatrixMath.translation(15f, -25f)
        val (x, y) = MatrixMath.mapPoint(translation, 10f, 50f)
        assertThat(x).isWithin(1e-5f).of(25f)
        assertThat(y).isWithin(1e-5f).of(25f)
    }

    @Test
    fun `scale scales coordinates by specified factors`() {
        val scale = MatrixMath.scale(2.5f, 0.5f)
        val (x, y) = MatrixMath.mapPoint(scale, 100f, 200f)
        assertThat(x).isWithin(1e-5f).of(250f)
        assertThat(y).isWithin(1e-5f).of(100f)
    }

    @Test
    fun `multiply correctly composes scale and translation`() {
        val t = MatrixMath.translation(50f, 50f)
        val s = MatrixMath.scale(2f, 2f)
        // Composite M = T * S -> scales first, then translates
        val m = MatrixMath.multiply(t, s)
        val (x, y) = MatrixMath.mapPoint(m, 10f, 20f)
        assertThat(x).isWithin(1e-5f).of(70f) // 10*2 + 50 = 70
        assertThat(y).isWithin(1e-5f).of(90f) // 20*2 + 50 = 90
    }

    @Test
    fun `invert inverts invertible matrix`() {
        val s = MatrixMath.scale(4f, 5f)
        val inv = MatrixMath.invert(s)
        assertThat(inv).isNotNull()

        val (x, y) = MatrixMath.mapPoint(inv!!, 100f, 100f)
        assertThat(x).isWithin(1e-5f).of(25f)
        assertThat(y).isWithin(1e-5f).of(20f)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        val singular = FloatArray(9) { 0f }
        assertThat(MatrixMath.invert(singular)).isNull()
    }
}
