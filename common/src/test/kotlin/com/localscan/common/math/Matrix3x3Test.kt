package com.localscan.common.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Matrix3x3Test {

    @Test
    fun `identity maps point to identical coordinates`() {
        val identity = Matrix3x3.IDENTITY
        val (x, y) = identity.mapPoint(35.5f, 78.2f)
        assertThat(x).isWithin(1e-5f).of(35.5f)
        assertThat(y).isWithin(1e-5f).of(78.2f)
    }

    @Test
    fun `translation shifts point by offsets`() {
        val t = Matrix3x3.translation(20f, -40f)
        val (x, y) = t.mapPoint(10f, 50f)
        assertThat(x).isWithin(1e-5f).of(30f)
        assertThat(y).isWithin(1e-5f).of(10f)
    }

    @Test
    fun `scale multiplies point by factors`() {
        val s = Matrix3x3.scale(3f, 0.5f)
        val (x, y) = s.mapPoint(100f, 200f)
        assertThat(x).isWithin(1e-5f).of(300f)
        assertThat(y).isWithin(1e-5f).of(100f)
    }

    @Test
    fun `times operator composes transformations correctly`() {
        val t = Matrix3x3.translation(10f, 20f)
        val s = Matrix3x3.scale(2f, 3f)
        // M = T * S -> scale first, then translate
        val m = t * s
        val (x, y) = m.mapPoint(5f, 5f)
        assertThat(x).isWithin(1e-5f).of(20f) // 5*2 + 10 = 20
        assertThat(y).isWithin(1e-5f).of(35f) // 5*3 + 20 = 35
    }

    @Test
    fun `invert inverts matrix and round-trips points`() {
        val t = Matrix3x3.translation(15f, 30f)
        val s = Matrix3x3.scale(2.5f, 1.8f)
        val m = t * s

        val inv = m.invert()
        assertThat(inv).isNotNull()

        val (origX, origY) = 42f to 99f
        val (mappedX, mappedY) = m.mapPoint(origX, origY)
        val (roundX, roundY) = inv!!.mapPoint(mappedX, mappedY)

        assertThat(roundX).isWithin(1e-4f).of(origX)
        assertThat(roundY).isWithin(1e-4f).of(origY)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        val zero = Matrix3x3(FloatArray(9) { 0f })
        assertThat(zero.invert()).isNull()
    }

    @Test
    fun `rotationUnitSquare 0 degrees is identity`() {
        val r0 = Matrix3x3.rotationUnitSquare(0)
        val (x, y) = r0.mapPoint(0.3f, 0.7f)
        assertThat(x).isWithin(1e-5f).of(0.3f)
        assertThat(y).isWithin(1e-5f).of(0.7f)
    }

    @Test
    fun `rotationUnitSquare 90 degrees rotates clockwise`() {
        val r90 = Matrix3x3.rotationUnitSquare(90)
        // (0,0) -> (1,0)
        val (x00, y00) = r90.mapPoint(0f, 0f)
        assertThat(x00).isWithin(1e-5f).of(1f)
        assertThat(y00).isWithin(1e-5f).of(0f)

        // (1,0) -> (1,1)
        val (x10, y10) = r90.mapPoint(1f, 0f)
        assertThat(x10).isWithin(1e-5f).of(1f)
        assertThat(y10).isWithin(1e-5f).of(1f)

        // (1,1) -> (0,1)
        val (x11, y11) = r90.mapPoint(1f, 1f)
        assertThat(x11).isWithin(1e-5f).of(0f)
        assertThat(y11).isWithin(1e-5f).of(1f)

        // (0,1) -> (0,0)
        val (x01, y01) = r90.mapPoint(0f, 1f)
        assertThat(x01).isWithin(1e-5f).of(0f)
        assertThat(y01).isWithin(1e-5f).of(0f)
    }

    @Test
    fun `rotationUnitSquare 180 and 270 degrees rotate correctly`() {
        val r180 = Matrix3x3.rotationUnitSquare(180)
        val (x1, y1) = r180.mapPoint(0.2f, 0.4f)
        assertThat(x1).isWithin(1e-5f).of(0.8f)
        assertThat(y1).isWithin(1e-5f).of(0.6f)

        val r270 = Matrix3x3.rotationUnitSquare(270)
        val (x00, y00) = r270.mapPoint(0f, 0f)
        assertThat(x00).isWithin(1e-5f).of(0f)
        assertThat(y00).isWithin(1e-5f).of(1f)

        // R(90) * R(270) should equal identity
        val r360 = Matrix3x3.rotationUnitSquare(90) * r270
        val (xTest, yTest) = 0.35f to 0.65f
        val (xRes, yRes) = r360.mapPoint(xTest, yTest)
        assertThat(xRes).isWithin(1e-5f).of(xTest)
        assertThat(yRes).isWithin(1e-5f).of(yTest)
    }

    @Test
    fun `homographyFromQuadToRect maps arbitrary quadrilateral to exact rectangle`() {
        val (x0, y0) = 120f to 100f // Top-Left
        val (x1, y1) = 850f to 160f // Top-Right
        val (x2, y2) = 890f to 750f // Bottom-Right
        val (x3, y3) = 150f to 720f // Bottom-Left

        val dstW = 1000f
        val dstH = 800f

        val h = Matrix3x3.homographyFromQuadToRect(x0, y0, x1, y1, x2, y2, x3, y3, dstW, dstH)

        val (m0x, m0y) = h.mapPoint(x0, y0)
        val (m1x, m1y) = h.mapPoint(x1, y1)
        val (m2x, m2y) = h.mapPoint(x2, y2)
        val (m3x, m3y) = h.mapPoint(x3, y3)

        assertThat(m0x).isWithin(1e-3f).of(0f)
        assertThat(m0y).isWithin(1e-3f).of(0f)

        assertThat(m1x).isWithin(1e-3f).of(dstW)
        assertThat(m1y).isWithin(1e-3f).of(0f)

        assertThat(m2x).isWithin(1e-3f).of(dstW)
        assertThat(m2y).isWithin(1e-3f).of(dstH)

        assertThat(m3x).isWithin(1e-3f).of(0f)
        assertThat(m3y).isWithin(1e-3f).of(dstH)

        // Check inverse mapping
        val hInv = h.invert()
        assertThat(hInv).isNotNull()
        val (orig0x, orig0y) = hInv!!.mapPoint(0f, 0f)
        assertThat(orig0x).isWithin(1e-3f).of(x0)
        assertThat(orig0y).isWithin(1e-3f).of(y0)
    }

    @Test
    fun `equals and hashCode obey value equality`() {
        val m1 = Matrix3x3.translation(10f, 20f)
        val m2 = Matrix3x3.translation(10f, 20f)
        val m3 = Matrix3x3.translation(10f, 25f)

        assertThat(m1).isEqualTo(m2)
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode())
        assertThat(m1).isNotEqualTo(m3)
    }
}
