package com.yscanner.common.math

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs

class MatrixMathAdversarialTest {

    // -------------------------------------------------------------------------
    // 1. Singular (non-invertible) matrices (determinant = 0)
    // -------------------------------------------------------------------------

    @Test
    fun `invert returns null for zero matrix`() {
        val zero = FloatArray(9) { 0f }
        assertThat(MatrixMath.invert(zero)).isNull()
    }

    @Test
    fun `invert returns null for all-ones matrix (rank 1)`() {
        val allOnes = FloatArray(9) { 1f }
        assertThat(MatrixMath.invert(allOnes)).isNull()
    }

    @Test
    fun `invert returns null for identical rows (rank 2)`() {
        val identicalRows = floatArrayOf(
            1f, 2f, 3f,
            1f, 2f, 3f,
            4f, 5f, 6f
        )
        assertThat(MatrixMath.invert(identicalRows)).isNull()
    }

    @Test
    fun `invert returns null for linearly dependent rows`() {
        // Row 2 = Row 0 + Row 1
        val depRows = floatArrayOf(
            1f, 2f, 3f,
            4f, 5f, 6f,
            5f, 7f, 9f
        )
        assertThat(MatrixMath.invert(depRows)).isNull()
    }

    @Test
    fun `invert returns null for scale matrix with zero axis`() {
        val zeroX = MatrixMath.scale(0f, 5f)
        val zeroY = MatrixMath.scale(5f, 0f)
        assertThat(MatrixMath.invert(zeroX)).isNull()
        assertThat(MatrixMath.invert(zeroY)).isNull()
    }

    @Test
    fun `invert returns null when determinant is below 1e-9 threshold`() {
        // scale(1e-5f, 1e-5f) has det = 1e-10f < 1e-9f
        val tinyScale = MatrixMath.scale(1e-5f, 1e-5f)
        assertThat(MatrixMath.invert(tinyScale)).isNull()
    }

    @Test
    fun `invert succeeds when determinant is slightly above 1e-9 threshold`() {
        // scale(1e-4f, 1e-4f) has det = 1e-8f > 1e-9f
        val smallScale = MatrixMath.scale(1e-4f, 1e-4f)
        val inv = MatrixMath.invert(smallScale)
        assertThat(inv).isNotNull()

        // Multiply by inverse should yield identity
        val identityCandidate = MatrixMath.multiply(smallScale, inv!!)
        val expectedIdentity = MatrixMath.identity()
        for (i in 0..8) {
            assertThat(identityCandidate[i]).isWithin(1e-3f).of(expectedIdentity[i])
        }
    }

    // -------------------------------------------------------------------------
    // 2. Projective transformations with w != 1 (perspective projection normalization)
    // -------------------------------------------------------------------------

    @Test
    fun `mapPoint normalizes correctly when projective w is not 1`() {
        // Projective matrix with m[6] = 0.01, m[7] = 0.02, m[8] = 1.0
        val p = floatArrayOf(
            2f, 0f, 10f,
            0f, 3f, 20f,
            0.01f, 0.02f, 1.0f
        )
        val (px, py) = MatrixMath.mapPoint(p, 100f, 50f)
        // w = 0.01 * 100 + 0.02 * 50 + 1.0 = 1.0 + 1.0 + 1.0 = 3.0
        // px = (2 * 100 + 0 + 10) / 3.0 = 210 / 3.0 = 70.0
        // py = (0 + 3 * 50 + 20) / 3.0 = 170 / 3.0 = 56.666668
        assertThat(px).isWithin(1e-4f).of(70f)
        assertThat(py).isWithin(1e-4f).of(56.666668f)
    }

    @Test
    fun `mapPoint satisfies projective scale invariance for homogeneous scalar multiples`() {
        val p = floatArrayOf(
            1.5f, 0.5f, 12f,
            -0.2f, 2.0f, 8f,
            0.005f, 0.01f, 1.0f
        )
        val (baseX, baseY) = MatrixMath.mapPoint(p, 80f, 60f)

        // Scale matrix by scalar k = 5.0
        val pScaledPositive = FloatArray(9) { p[it] * 5.0f }
        val (scaledX1, scaledY1) = MatrixMath.mapPoint(pScaledPositive, 80f, 60f)
        assertThat(scaledX1).isWithin(1e-4f).of(baseX)
        assertThat(scaledY1).isWithin(1e-4f).of(baseY)

        // Scale matrix by scalar k = -2.0 (negative homogeneous scalar preserves projective point)
        val pScaledNegative = FloatArray(9) { p[it] * -2.0f }
        val (scaledX2, scaledY2) = MatrixMath.mapPoint(pScaledNegative, 80f, 60f)
        assertThat(scaledX2).isWithin(1e-4f).of(baseX)
        assertThat(scaledY2).isWithin(1e-4f).of(baseY)
    }

    // -------------------------------------------------------------------------
    // 3. Point at infinity / near-zero w handling
    // -------------------------------------------------------------------------

    @Test
    fun `mapPoint handles exact zero w by replacing w with 1f (fallback behavior)`() {
        // Matrix with m[6] = 1, m[7] = 0, m[8] = 0 -> w = x
        val p = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            1f, 0f, 0f
        )
        // At x = 0, w = 0, which triggers fallback w = 1f
        val (px, py) = MatrixMath.mapPoint(p, 0f, 10f)
        assertThat(px).isEqualTo(0f)
        assertThat(py).isEqualTo(10f)
    }

    @Test
    fun `mapPoint demonstrates discontinuous cliff at near-zero w threshold 1e-7f`() {
        // Matrix where w = x
        val p = floatArrayOf(
            1f, 0f, 10f,
            0f, 1f, 20f,
            1f, 0f, 0f
        )

        // Just above threshold: w = 2e-7f (no fallback, w retained)
        val (xAbove, yAbove) = MatrixMath.mapPoint(p, 2e-7f, 5f)
        // px = (2e-7 + 10) / 2e-7 ≈ 50,000,000
        assertThat(xAbove).isGreaterThan(1e7f)

        // Just below threshold: w = 0.5e-7f (fallback triggers w = 1f)
        val (xBelow, yBelow) = MatrixMath.mapPoint(p, 0.5e-7f, 5f)
        // px = (0.5e-7 + 10) / 1.0 = 10.00000005
        assertThat(xBelow).isWithin(1e-5f).of(10f)
    }

    // -------------------------------------------------------------------------
    // 4. Associativity of matrix multiplication: (A * B) * C == A * (B * C)
    // -------------------------------------------------------------------------

    @Test
    fun `matrix multiplication is associative for affine transformations`() {
        val a = MatrixMath.translation(25f, -40f)
        val b = MatrixMath.rotation(37.5f)
        val c = MatrixMath.scale(1.75f, 0.85f)

        val ab_c = MatrixMath.multiply(MatrixMath.multiply(a, b), c)
        val a_bc = MatrixMath.multiply(a, MatrixMath.multiply(b, c))

        for (i in 0..8) {
            assertThat(ab_c[i]).isWithin(1e-5f).of(a_bc[i])
        }
    }

    @Test
    fun `matrix multiplication is associative for projective homographies`() {
        val h1 = floatArrayOf(
            1.2f, 0.3f, 15f,
            -0.1f, 0.9f, 25f,
            0.001f, -0.002f, 1f
        )
        val h2 = floatArrayOf(
            0.8f, -0.4f, 5f,
            0.2f, 1.1f, -10f,
            -0.0005f, 0.001f, 1f
        )
        val h3 = floatArrayOf(
            1.05f, 0.05f, -2f,
            -0.03f, 0.98f, 7f,
            0.0002f, 0.0001f, 1f
        )

        val h12_3 = MatrixMath.multiply(MatrixMath.multiply(h1, h2), h3)
        val h1_23 = MatrixMath.multiply(h1, MatrixMath.multiply(h2, h3))

        for (i in 0..8) {
            assertThat(h12_3[i]).isWithin(1e-4f).of(h1_23[i])
        }
    }

    // -------------------------------------------------------------------------
    // 5. Input validation and sizing contracts
    // -------------------------------------------------------------------------

    @Test
    fun `multiply throws IllegalArgumentException when matrix size is not 9`() {
        assertThrows(IllegalArgumentException::class.java) {
            MatrixMath.multiply(FloatArray(8), FloatArray(9))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatrixMath.multiply(FloatArray(9), FloatArray(10))
        }
    }

    @Test
    fun `invert throws IllegalArgumentException when matrix size is not 9`() {
        assertThrows(IllegalArgumentException::class.java) {
            MatrixMath.invert(FloatArray(8))
        }
    }

    @Test
    fun `mapPoint throws IllegalArgumentException when matrix size is not 9`() {
        assertThrows(IllegalArgumentException::class.java) {
            MatrixMath.mapPoint(FloatArray(4), 0f, 0f)
        }
    }
}
