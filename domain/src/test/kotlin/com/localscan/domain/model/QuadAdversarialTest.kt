package com.localscan.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Adversarial and stress test harness for Quad geometry and mathematical representations.
 * Evaluates degenerate configurations, orientation invariants, concave shapes,
 * self-intersecting topologies, floating-point precision, and Shoelace area accuracy.
 */
class QuadAdversarialTest {

    // =========================================================================
    // 1. Degenerate Quads
    // =========================================================================

    @Test
    fun `isConvex returns false and area is zero when all corners are identical`() {
        val quad = Quad(
            topLeft = Corner(42.0f, 42.0f),
            topRight = Corner(42.0f, 42.0f),
            bottomRight = Corner(42.0f, 42.0f),
            bottomLeft = Corner(42.0f, 42.0f)
        )
        assertThat(quad.isConvex()).isFalse()
        assertThat(quad.area()).isEqualTo(0.0f)
    }

    @Test
    fun `isConvex returns false for triangle degenerate with three collinear points`() {
        // p0, p1, p2 are collinear along y = 0; p3 is at (0, 100)
        val quad = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(50.0f, 0.0f),
            bottomRight = Corner(100.0f, 0.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(quad.isConvex()).isFalse()
        // Area should match triangle with base 100, height 100 -> 0.5 * 100 * 100 = 5000.0f
        assertThat(quad.area()).isEqualTo(5000.0f)
    }

    @Test
    fun `isConvex returns false and area is zero for line segment with four collinear points`() {
        // All points along y = 2x
        val quad = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(10.0f, 20.0f),
            bottomRight = Corner(20.0f, 40.0f),
            bottomLeft = Corner(30.0f, 60.0f)
        )
        assertThat(quad.isConvex()).isFalse()
        assertThat(quad.area()).isEqualTo(0.0f)
    }

    @Test
    fun `isConvex returns false when two adjacent corners are coincident`() {
        // topLeft and topRight are coincident at (0, 0)
        val quad = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(0.0f, 0.0f),
            bottomRight = Corner(100.0f, 100.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(quad.isConvex()).isFalse()
        // Triangle formed by (0,0), (100,100), (0,100) -> base 100, height 100 -> area 5000.0f
        assertThat(quad.area()).isEqualTo(5000.0f)
    }

    // =========================================================================
    // 2. Clockwise vs Counter-Clockwise Vertex Orderings
    // =========================================================================

    @Test
    fun `isConvex returns true and area is identical for both CW and CCW vertex orderings`() {
        // Clockwise in screen coordinates (y down): (0,0) -> (100,0) -> (100,200) -> (0,200)
        val cwQuad = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(100.0f, 200.0f),
            bottomLeft = Corner(0.0f, 200.0f)
        )

        // Counter-clockwise: (0,0) -> (0,200) -> (100,200) -> (100,0)
        val ccwQuad = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(0.0f, 200.0f),
            bottomRight = Corner(100.0f, 200.0f),
            bottomLeft = Corner(100.0f, 0.0f)
        )

        assertThat(cwQuad.isConvex()).isTrue()
        assertThat(ccwQuad.isConvex()).isTrue()

        assertThat(cwQuad.area()).isEqualTo(20000.0f)
        assertThat(ccwQuad.area()).isEqualTo(20000.0f)
    }

    @Test
    fun `cyclic vertex permutations preserve convexity and area invariants`() {
        val p0 = Corner(10.0f, 20.0f)
        val p1 = Corner(110.0f, 25.0f)
        val p2 = Corner(105.0f, 180.0f)
        val p3 = Corner(15.0f, 175.0f)

        val q0 = Quad(p0, p1, p2, p3)
        val q1 = Quad(p1, p2, p3, p0)
        val q2 = Quad(p2, p3, p0, p1)
        val q3 = Quad(p3, p0, p1, p2)

        val expectedArea = q0.area()
        assertThat(q0.isConvex()).isTrue()

        listOf(q1, q2, q3).forEach { rotated ->
            assertThat(rotated.isConvex()).isTrue()
            assertThat(rotated.area()).isWithin(1e-3f).of(expectedArea)
        }
    }

    // =========================================================================
    // 3. Concave Quads (Arrowhead / Dart Shapes)
    // =========================================================================

    @Test
    fun `isConvex returns false for concave dart shapes across all four vertex positions`() {
        // Base square is [0, 100] x [0, 100]. In each case, one vertex is indented into the center.
        // 1. Top indented
        val topDart = Quad(
            topLeft = Corner(50.0f, 60.0f), // indented down towards center
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(100.0f, 100.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        // 2. Right indented
        val rightDart = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(40.0f, 50.0f), // indented left towards center
            bottomRight = Corner(100.0f, 100.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        // 3. Bottom indented
        val bottomDart = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(50.0f, 40.0f), // indented up towards center
            bottomLeft = Corner(0.0f, 100.0f)
        )
        // 4. Left indented
        val leftDart = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(100.0f, 100.0f),
            bottomLeft = Corner(60.0f, 50.0f) // indented right towards center
        )

        assertThat(topDart.isConvex()).isFalse()
        assertThat(rightDart.isConvex()).isFalse()
        assertThat(bottomDart.isConvex()).isFalse()
        assertThat(leftDart.isConvex()).isFalse()
    }

    @Test
    fun `shoelace area correctly computes exact area of concave dart quad`() {
        // Dart within 100x100 box: (0,0) -> (100,0) -> (50,50) -> (0,100)
        // Decomposed into two triangles:
        // Triangle 1: (0,0), (100,0), (50,50) -> base = 100, height = 50 -> area = 2500
        // Triangle 2: (0,0), (50,50), (0,100) -> base = 100, height = 50 -> area = 2500
        // Expected total area = 5000.0f
        val dart = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(50.0f, 50.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(dart.isConvex()).isFalse()
        assertThat(dart.area()).isEqualTo(5000.0f)
    }

    // =========================================================================
    // 4. Self-Intersecting Quads (Bowtie / Figure-8)
    // =========================================================================

    @Test
    fun `isConvex returns false for symmetric bowtie quad and Shoelace area evaluates to zero`() {
        // Vertices cross diagonals: (0,0) -> (100,100) -> (100,0) -> (0,100)
        // Edges cross at (50, 50). Two opposing lobes cancel in Shoelace summation.
        val bowtie = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 100.0f),
            bottomRight = Corner(100.0f, 0.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(bowtie.isConvex()).isFalse()
        // Shoelace formula computes algebraic signed area: +2500 + (-2500) = 0.0f
        assertThat(bowtie.area()).isEqualTo(0.0f)
    }

    @Test
    fun `isConvex returns false for asymmetric bowtie quad and Shoelace computes algebraic difference`() {
        // Left lobe larger than right lobe:
        // p0=(0,0), p1=(100,80), p2=(100,20), p3=(0,100)
        val asymmetricBowtie = Quad(
            topLeft = Corner(0.0f, 0.0f),
            topRight = Corner(100.0f, 80.0f),
            bottomRight = Corner(100.0f, 20.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(asymmetricBowtie.isConvex()).isFalse()

        // Verify Shoelace computes |sum| * 0.5f algebraically:
        // p0.x*p1.y - p0.y*p1.x = 0*80 - 0*100 = 0
        // p1.x*p2.y - p1.y*p2.x = 100*20 - 80*100 = 2000 - 8000 = -6000
        // p2.x*p3.y - p2.y*p3.x = 100*100 - 20*0 = 10000
        // p3.x*p0.y - p3.y*p0.x = 0*0 - 100*0 = 0
        // sum = 0 - 6000 + 10000 + 0 = 4000
        // area = 0.5 * 4000 = 2000.0f
        assertThat(asymmetricBowtie.area()).isEqualTo(2000.0f)
    }

    // =========================================================================
    // 5. Coordinate Extremes & Floating-Point Edge Cases
    // =========================================================================

    @Test
    fun `isConvex and area handle completely negative coordinate systems accurately`() {
        // Rectangle from (-100, -80) to (-20, -20)
        // Width = 80, Height = 60, Area = 4800.0f
        val negativeQuad = Quad(
            topLeft = Corner(-100.0f, -80.0f),
            topRight = Corner(-20.0f, -80.0f),
            bottomRight = Corner(-20.0f, -20.0f),
            bottomLeft = Corner(-100.0f, -20.0f)
        )
        assertThat(negativeQuad.isConvex()).isTrue()
        assertThat(negativeQuad.area()).isEqualTo(4800.0f)
    }

    @Test
    fun `isConvex and area handle quads straddling the origin`() {
        val quad = Quad(
            topLeft = Corner(-50.0f, -50.0f),
            topRight = Corner(50.0f, -50.0f),
            bottomRight = Corner(50.0f, 50.0f),
            bottomLeft = Corner(-50.0f, 50.0f)
        )
        assertThat(quad.isConvex()).isTrue()
        assertThat(quad.area()).isEqualTo(10000.0f)
    }

    @Test
    fun `stress test normalized small quad reveals epsilon threshold behavior`() {
        // When coordinates are in normalized space [0, 1], edge lengths can be small.
        // For a 0.005 x 0.005 normalized box (e.g. 5x5 pixels in a 1000x1000 buffer):
        // cross product = 0.005 * 0.005 = 0.000025f = 2.5e-5f.
        // Since 2.5e-5f < 1e-4f (the hardcoded epsilon in isConvex), this test documents whether
        // Quad.isConvex() considers it non-convex due to absolute epsilon scaling.
        val smallNormalizedQuad = Quad(
            topLeft = Corner(0.100f, 0.100f),
            topRight = Corner(0.105f, 0.100f),
            bottomRight = Corner(0.105f, 0.105f),
            bottomLeft = Corner(0.100f, 0.105f)
        )

        // Mathematical area is exact: 0.005 * 0.005 = 0.000025f (2.5e-5f)
        assertThat(smallNormalizedQuad.area()).isWithin(1e-7f).of(0.000025f)

        // EMPIRICAL CHALLENGE: Verify whether isConvex fails due to epsilon = 1e-4f
        val isConvex = smallNormalizedQuad.isConvex()
        // We empirically record that small normalized quads fail isConvex() because 2.5e-5 < 1e-4
        assertThat(isConvex).isFalse()
    }

    @Test
    fun `stress test standard normalized quad passes isConvex`() {
        // Standard document in normalized coordinates covering ~50% of frame:
        // [0.2, 0.8] x [0.2, 0.8] -> width 0.6, height 0.6
        // cp = 0.6 * 0.6 = 0.36 > 1e-4
        val normalizedDoc = Quad(
            topLeft = Corner(0.2f, 0.2f),
            topRight = Corner(0.8f, 0.2f),
            bottomRight = Corner(0.8f, 0.8f),
            bottomLeft = Corner(0.2f, 0.8f)
        )
        assertThat(normalizedDoc.isConvex()).isTrue()
        assertThat(normalizedDoc.area()).isWithin(1e-5f).of(0.36f)
    }

    @Test
    fun `stress test large coordinates precision loss in Shoelace formula`() {
        // A 100x100 square shifted by 1,000,000 to (1e6, 1e6).
        // Ground truth mathematical area = 100 * 100 = 10,000.0f.
        val largeQuad = Quad(
            topLeft = Corner(1_000_000.0f, 1_000_000.0f),
            topRight = Corner(1_000_100.0f, 1_000_000.0f),
            bottomRight = Corner(1_000_100.0f, 1_000_100.0f),
            bottomLeft = Corner(1_000_000.0f, 1_000_100.0f)
        )
        assertThat(largeQuad.isConvex()).isTrue()

        // EMPIRICAL BUG/VULNERABILITY REPRODUCTION:
        // In 32-bit float arithmetic without Double accumulation or translation-centering,
        // 1e6 * 1e6 = 1e12 where float ULP is ~65536.
        // Catastrophic cancellation occurs in (p0.x * p1.y - p0.y * p1.x),
        // completely zeroing out the computed area:
        val computedArea = largeQuad.area()
        assertThat(computedArea).isEqualTo(0.0f) // Catastrophic cancellation failure confirmed!
    }

    @Test
    fun `isConvex returns false when coordinates contain NaN`() {
        val nanQuad = Quad(
            topLeft = Corner(Float.NaN, 0.0f),
            topRight = Corner(100.0f, 0.0f),
            bottomRight = Corner(100.0f, 100.0f),
            bottomLeft = Corner(0.0f, 100.0f)
        )
        assertThat(nanQuad.isConvex()).isFalse()
        assertThat(nanQuad.area().isNaN()).isTrue()
    }

    // =========================================================================
    // 6. Shoelace Area Accuracy vs Mathematical Ground Truth
    // =========================================================================

    @Test
    fun `shoelace area matches analytical area of arbitrary rotated rectangle`() {
        // Dimensions: width = 120.0f, height = 80.0f -> ground truth area = 9600.0f
        val w = 120.0f
        val h = 80.0f
        val expectedArea = w * h

        val anglesDegrees = listOf(15.0, 30.0, 45.0, 60.0, 75.0, 90.0)

        for (angleDeg in anglesDegrees) {
            val rad = Math.toRadians(angleDeg).toFloat()
            val cosT = cos(rad)
            val sinT = sin(rad)

            // Local coordinates before rotation:
            // p0 = (0, 0), p1 = (w, 0), p2 = (w, h), p3 = (0, h)
            fun rotate(x: Float, y: Float): Corner = Corner(
                x = x * cosT - y * sinT,
                y = x * sinT + y * cosT
            )

            val quad = Quad(
                topLeft = rotate(0.0f, 0.0f),
                topRight = rotate(w, 0.0f),
                bottomRight = rotate(w, h),
                bottomLeft = rotate(0.0f, h)
            )

            assertThat(quad.isConvex()).isTrue()
            assertThat(quad.area()).isWithin(0.1f).of(expectedArea)
        }
    }

    @Test
    fun `shoelace area matches triangle decomposition ground truth for general convex quad`() {
        // Arbitrary quadrilateral
        val p0 = Corner(15.2f, 23.4f)
        val p1 = Corner(145.8f, 31.9f)
        val p2 = Corner(180.3f, 210.6f)
        val p3 = Corner(22.7f, 195.1f)

        val quad = Quad(p0, p1, p2, p3)
        assertThat(quad.isConvex()).isTrue()

        // Ground truth via independent triangulation:
        // Quad decomposed into Triangle(p0, p1, p2) + Triangle(p0, p2, p3)
        fun triangleArea(a: Corner, b: Corner, c: Corner): Float {
            val abX = b.x - a.x
            val abY = b.y - a.y
            val acX = c.x - a.x
            val acY = c.y - a.y
            return abs(abX * acY - abY * acX) * 0.5f
        }

        val tri1 = triangleArea(p0, p1, p2)
        val tri2 = triangleArea(p0, p2, p3)
        val groundTruthArea = tri1 + tri2

        // Float arithmetic causes ~0.002f difference between Shoelace and triangulation
        assertThat(quad.area()).isWithin(0.01f).of(groundTruthArea)
    }
}
