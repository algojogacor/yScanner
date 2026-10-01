package com.yscanner.geometry

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Test
import kotlin.math.hypot

/**
 * Tests for quadrilateral fitting and corner refinement — the heart of M04.
 *
 * The corner-ORDER assertions matter most: `Quad` requires
 * `topLeft -> topRight -> bottomRight -> bottomLeft` clockwise, and a fitter that
 * returns the right four points in the wrong order silently corrupts every
 * downstream stage (perspective correction, PDF layout).
 */
class QuadrilateralFitterTest {

    private val fitter = DefaultQuadrilateralFitter()

    /** Distance between two corners, in pixels. */
    private fun distance(a: Corner, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    // ------------------------------------------------------------ orderCorners

    @Test
    fun `orderCorners sorts scrambled points into TL TR BR BL`() {
        val scrambled = listOf(
            PointF(100f, 100f), // BR
            PointF(0f, 0f),     // TL
            PointF(100f, 0f),   // TR
            PointF(0f, 100f)    // BL
        )

        val quad = orderCorners(scrambled)
        assertThat(quad).isNotNull()
        requireNotNull(quad)

        assertThat(quad.topLeft.x).isEqualTo(0f)
        assertThat(quad.topLeft.y).isEqualTo(0f)
        assertThat(quad.topRight.x).isEqualTo(100f)
        assertThat(quad.topRight.y).isEqualTo(0f)
        assertThat(quad.bottomRight.x).isEqualTo(100f)
        assertThat(quad.bottomRight.y).isEqualTo(100f)
        assertThat(quad.bottomLeft.x).isEqualTo(0f)
        assertThat(quad.bottomLeft.y).isEqualTo(100f)
    }

    @Test
    fun `orderCorners is invariant to input permutation`() {
        val base = listOf(
            PointF(10f, 20f), PointF(110f, 25f), PointF(105f, 120f), PointF(15f, 115f)
        )
        val reference = orderCorners(base)
        requireNotNull(reference)

        // Rotating the input should not change the output ordering.
        for (shift in 1..3) {
            val rotated = base.drop(shift) + base.take(shift)
            val quad = orderCorners(rotated)
            requireNotNull(quad)
            assertThat(quad.topLeft.x).isWithin(0.001f).of(reference.topLeft.x)
            assertThat(quad.topLeft.y).isWithin(0.001f).of(reference.topLeft.y)
            assertThat(quad.topRight.x).isWithin(0.001f).of(reference.topRight.x)
            assertThat(quad.bottomRight.x).isWithin(0.001f).of(reference.bottomRight.x)
            assertThat(quad.bottomLeft.x).isWithin(0.001f).of(reference.bottomLeft.x)
        }
    }

    @Test
    fun `orderCorners rejects wrong counts and coincident points`() {
        assertThat(orderCorners(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f)))).isNull()
        assertThat(
            orderCorners(
                listOf(PointF(0f, 0f), PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f))
            )
        ).isNull()
    }

    // ------------------------------------------------------------ fitting

    @Test
    fun `fits an axis-aligned rectangle within tolerance`() {
        val groundTruth = Quad(
            topLeft = Corner(20f, 20f),
            topRight = Corner(180f, 20f),
            bottomRight = Corner(180f, 180f),
            bottomLeft = Corner(20f, 180f)
        )
        val mask = BinaryMask.filledQuad(200, 200, groundTruth)
        val boundary = BoundaryExtractor(minComponentArea = 16).extract(mask).first()

        val fitted = fitter.fit(boundary)
        assertThat(fitted).isNotNull()
        requireNotNull(fitted)

        // Rasterisation traces pixel centres, so the recovered edge sits on the
        // last inside pixel (179) rather than the geometric edge (180).
        assertThat(distance(fitted.topLeft, PointF(20f, 20f))).isLessThan(2f)
        assertThat(distance(fitted.topRight, PointF(179f, 20f))).isLessThan(2f)
        assertThat(distance(fitted.bottomRight, PointF(179f, 179f))).isLessThan(2f)
        assertThat(distance(fitted.bottomLeft, PointF(20f, 179f))).isLessThan(2f)
    }

    @Test
    fun `fitted quad is convex and correctly ordered`() {
        val groundTruth = Quad(
            topLeft = Corner(20f, 20f),
            topRight = Corner(180f, 20f),
            bottomRight = Corner(180f, 180f),
            bottomLeft = Corner(20f, 180f)
        )
        val mask = BinaryMask.filledQuad(200, 200, groundTruth)
        val boundary = BoundaryExtractor(minComponentArea = 16).extract(mask).first()

        val fitted = requireNotNull(fitter.fit(boundary))

        assertThat(fitted.isConvex()).isTrue()
        // Ordering sanity: topLeft is the most top-left, topRight the most top-right, etc.
        assertThat(fitted.topLeft.x).isLessThan(fitted.topRight.x)
        assertThat(fitted.topLeft.y).isLessThan(fitted.bottomLeft.y)
        assertThat(fitted.topRight.y).isLessThan(fitted.bottomRight.y)
        assertThat(fitted.bottomLeft.x).isLessThan(fitted.bottomRight.x)
        // topLeft must minimise x + y among the four corners.
        val sums = listOf(
            fitted.topLeft.x + fitted.topLeft.y,
            fitted.topRight.x + fitted.topRight.y,
            fitted.bottomRight.x + fitted.bottomRight.y,
            fitted.bottomLeft.x + fitted.bottomLeft.y
        )
        assertThat(sums.min()).isWithin(0.001f).of(fitted.topLeft.x + fitted.topLeft.y)
    }

    @Test
    fun `fits a rotated rectangle and preserves corner order`() {
        // Rectangle centred at (100,100), 140x100, rotated 20 degrees.
        val cos = 0.9396926f
        val sin = 0.3420201f
        val ux = cos * 70f
        val uy = sin * 70f
        val vx = -sin * 50f
        val vy = cos * 50f

        val expectedTl = PointF(100f - ux - vx, 100f - uy - vy)
        val expectedTr = PointF(100f + ux - vx, 100f + uy - vy)
        val expectedBr = PointF(100f + ux + vx, 100f + uy + vy)
        val expectedBl = PointF(100f - ux + vx, 100f - uy + vy)

        val groundTruth = Quad(
            topLeft = Corner(expectedTl.x, expectedTl.y),
            topRight = Corner(expectedTr.x, expectedTr.y),
            bottomRight = Corner(expectedBr.x, expectedBr.y),
            bottomLeft = Corner(expectedBl.x, expectedBl.y)
        )
        val mask = BinaryMask.filledQuad(220, 220, groundTruth)
        val boundary = BoundaryExtractor(minComponentArea = 16).extract(mask).first()

        val fitted = fitter.fit(boundary)
        assertThat(fitted).isNotNull()
        requireNotNull(fitted)

        assertThat(fitted.isConvex()).isTrue()
        // Tolerance covers rasterisation plus the polygon simplification step.
        assertThat(distance(fitted.topLeft, expectedTl)).isLessThan(6f)
        assertThat(distance(fitted.topRight, expectedTr)).isLessThan(6f)
        assertThat(distance(fitted.bottomRight, expectedBr)).isLessThan(6f)
        assertThat(distance(fitted.bottomLeft, expectedBl)).isLessThan(6f)
    }

    @Test
    fun `fits a perspective-skewed trapezoid`() {
        // A trapezoid has four genuine extreme corners, so the fit should be tight.
        val groundTruth = Quad(
            topLeft = Corner(40f, 30f),
            topRight = Corner(180f, 50f),
            bottomRight = Corner(160f, 170f),
            bottomLeft = Corner(60f, 150f)
        )
        val mask = BinaryMask.filledQuad(220, 200, groundTruth)
        val boundary = BoundaryExtractor(minComponentArea = 16).extract(mask).first()

        val fitted = fitter.fit(boundary)
        assertThat(fitted).isNotNull()
        requireNotNull(fitted)

        assertThat(fitted.isConvex()).isTrue()
        assertThat(distance(fitted.topLeft, PointF(40f, 30f))).isLessThan(6f)
        assertThat(distance(fitted.topRight, PointF(180f, 50f))).isLessThan(6f)
        assertThat(distance(fitted.bottomRight, PointF(160f, 170f))).isLessThan(6f)
        assertThat(distance(fitted.bottomLeft, PointF(60f, 150f))).isLessThan(6f)
    }

    @Test
    fun `fit returns null for degenerate boundaries`() {
        assertThat(fitter.fit(Boundary(emptyList()))).isNull()
        assertThat(fitter.fit(Boundary(listOf(PointF(0f, 0f))))).isNull()
        assertThat(fitter.fit(Boundary(listOf(PointF(0f, 0f), PointF(1f, 1f))))).isNull()
        // All-collinear: zero area.
        assertThat(
            fitter.fit(Boundary(listOf(PointF(0f, 0f), PointF(5f, 0f), PointF(10f, 0f))))
        ).isNull()
    }

    @Test
    fun `fit rejects a sliver that is small relative to its bounding box`() {
        // A thin strip running along the diagonal: its own axis-aligned bounding box
        // is a large square (~201x201) while the strip itself is only ~560 px^2, so
        // quad.area()/boundingBox.area is ~0.014 and must fall below minAreaRatio.
        //
        // Note: an *axis-aligned* sliver cannot be rejected this way, because its
        // bounding box is the sliver itself and the ratio is 1.0 by construction.
        // The realistic false positive this gate exists for is a long diagonal edge
        // line detected as a document, not an axis-aligned bar.
        val diagonalSliver = Boundary(
            listOf(
                PointF(0f, 0f),
                PointF(200f, 200f),
                PointF(198.6f, 201.4f),
                PointF(-1.4f, 1.4f)
            )
        )
        assertThat(fitter.fit(diagonalSliver)).isNull()
    }

    @Test
    fun `axis-aligned bar is not rejected by the area-ratio gate`() {
        // Documents the actual semantics of minAreaRatio: it compares the fitted quad
        // against the boundary's *own* bounding box, so a shape that fills its bounding
        // box always passes regardless of how thin it is in absolute terms.
        val bar = Boundary(
            listOf(PointF(0f, 0f), PointF(200f, 0f), PointF(200f, 1f), PointF(0f, 1f))
        )
        assertThat(fitter.fit(bar)).isNotNull()
    }

    @Test
    fun `minimum area enclosing rectangle wraps a triangle hull`() {
        val hull = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(0f, 100f))
        val rectangle = minAreaEnclosingRectangle(hull)

        assertThat(rectangle).isNotNull()
        requireNotNull(rectangle)
        assertThat(rectangle).hasSize(4)
        // Every hull point must lie inside the enclosing rectangle.
        val enclosing = Boundary(rectangle)
        for (p in hull) {
            assertThat(enclosing.contains(p)).isTrue()
        }
    }

    @Test
    fun `minimum area enclosing rectangle rejects a degenerate hull`() {
        assertThat(minAreaEnclosingRectangle(listOf(PointF(0f, 0f), PointF(1f, 1f)))).isNull()
        assertThat(minAreaEnclosingRectangle(emptyList())).isNull()
    }

    // ------------------------------------------------------------ refinement

    @Test
    fun `refinement pulls an offset quad toward the mask`() {
        val groundTruth = Quad(
            topLeft = Corner(30f, 30f),
            topRight = Corner(130f, 30f),
            bottomRight = Corner(130f, 130f),
            bottomLeft = Corner(30f, 130f)
        )
        val mask = BinaryMask.filledQuad(170, 170, groundTruth)

        // Same shape, shifted 3px down-right.
        val offset = Quad(
            topLeft = Corner(33f, 33f),
            topRight = Corner(133f, 33f),
            bottomRight = Corner(133f, 133f),
            bottomLeft = Corner(33f, 133f)
        )

        val before = hypot(offset.topLeft.x - 30f, offset.topLeft.y - 30f)
        val refined = refineCorners(offset, mask, searchRadius = 12)
        val after = hypot(refined.topLeft.x - 30f, refined.topLeft.y - 30f)

        assertThat(after).isLessThan(before)
        assertThat(refined.isConvex()).isTrue()
    }

    @Test
    fun `refinement never moves a corner beyond the search radius`() {
        val mask = BinaryMask.filledQuad(
            200, 200,
            Quad(Corner(40f, 40f), Corner(160f, 40f), Corner(160f, 160f), Corner(40f, 160f))
        )
        // Start far away from any foreground; the clamp must hold.
        val far = Quad(
            topLeft = Corner(5f, 5f),
            topRight = Corner(195f, 5f),
            bottomRight = Corner(195f, 195f),
            bottomLeft = Corner(5f, 195f)
        )

        val radius = 10
        val refined = refineCorners(far, mask, searchRadius = radius)

        val originals = listOf(far.topLeft, far.topRight, far.bottomRight, far.bottomLeft)
        val results = listOf(refined.topLeft, refined.topRight, refined.bottomRight, refined.bottomLeft)
        for (i in 0 until 4) {
            val moved = hypot(
                (results[i].x - originals[i].x).toDouble(),
                (results[i].y - originals[i].y).toDouble()
            )
            assertThat(moved).isAtMost(radius.toDouble() + 0.001)
        }
    }

    @Test
    fun `refinement returns the input unchanged for an empty mask`() {
        val quad = Quad(Corner(0f, 0f), Corner(10f, 0f), Corner(10f, 10f), Corner(0f, 10f))
        val empty = BinaryMask(20, 20, ByteArray(400))

        assertThat(refineCorners(quad, empty, searchRadius = 5)).isEqualTo(quad)
        assertThat(refineCorners(quad, empty, searchRadius = 0)).isEqualTo(quad)
    }

    @Test
    fun `refinement preserves corner confidence values`() {
        val quad = Quad(
            topLeft = Corner(30f, 30f, 0.9f),
            topRight = Corner(130f, 30f, 0.8f),
            bottomRight = Corner(130f, 130f, 0.7f),
            bottomLeft = Corner(30f, 130f, 0.6f)
        )
        val mask = BinaryMask.filledQuad(170, 170, quad)
        val refined = refineCorners(quad, mask, searchRadius = 4)

        assertThat(refined.topLeft.confidence).isEqualTo(0.9f)
        assertThat(refined.topRight.confidence).isEqualTo(0.8f)
        assertThat(refined.bottomRight.confidence).isEqualTo(0.7f)
        assertThat(refined.bottomLeft.confidence).isEqualTo(0.6f)
    }

    // ------------------------------------------------------------ end to end

    @Test
    fun `mask to quad pipeline recovers a known document shape`() {
        // Full M04 path: mask -> boundary -> envelope -> quad.
        val groundTruth = Quad(
            topLeft = Corner(50f, 40f),
            topRight = Corner(210f, 60f),
            bottomRight = Corner(190f, 200f),
            bottomLeft = Corner(70f, 180f)
        )
        val mask = BinaryMask.filledQuad(260, 240, groundTruth)

        val boundary = BoundaryExtractor(minComponentArea = 64).extract(mask).first()
        val quad = requireNotNull(fitter.fit(boundary))

        assertThat(quad.isConvex()).isTrue()
        assertThat(quad.area()).isGreaterThan(0f)
        // The recovered quad must enclose the bulk of the original shape.
        assertThat(quad.area()).isAtLeast(boundary.area() * 0.7f)
    }
}
