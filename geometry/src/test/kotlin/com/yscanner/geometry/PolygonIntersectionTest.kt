package com.yscanner.geometry

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Test

/**
 * Tests for convex polygon intersection and IoU.
 *
 * The winding-invariance cases are the ones that matter: the production caller passes
 * [Quad]s in TL → TR → BR → BL order (positive [signedPolygonArea]), but a quad that has
 * round-tripped through another producer can come back reversed. If the clipper only works
 * for one winding it returns an empty intersection for correctly-overlapping shapes — a
 * silent failure that would make target tracking drop every frame.
 */
class PolygonIntersectionTest {

    /** Axis-aligned rectangle in TL → TR → BR → BL order (positive shoelace area). */
    private fun square(x0: Float, y0: Float, x1: Float, y1: Float): List<PointF> = listOf(
        PointF(x0, y0), PointF(x1, y0), PointF(x1, y1), PointF(x0, y1)
    )

    private fun quad(x0: Float, y0: Float, x1: Float, y1: Float): Quad = Quad(
        topLeft = Corner(x0, y0),
        topRight = Corner(x1, y0),
        bottomRight = Corner(x1, y1),
        bottomLeft = Corner(x0, y1)
    )

    // ---------------------------------------------------------------- identity & disjoint

    @Test
    fun `identical squares have IoU of one`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(0f, 0f, 10f, 10f)

        assertThat(polygonIoU(a, b)).isWithin(1e-4f).of(1f)
        assertThat(polygonIntersectionArea(a, b)).isWithin(0.01f).of(100f)
    }

    @Test
    fun `fully disjoint squares have IoU of exactly zero`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(20f, 20f, 30f, 30f)

        assertThat(polygonIoU(a, b)).isEqualTo(0f)
        assertThat(convexPolygonIntersection(a, b)).isEmpty()
    }

    @Test
    fun `contained square IoU equals the area ratio`() {
        val outer = square(0f, 0f, 20f, 20f)
        val inner = square(5f, 5f, 15f, 15f)

        // 100 / 400
        assertThat(polygonIoU(outer, inner)).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `half overlapping squares have IoU of one third`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(5f, 0f, 15f, 10f)

        // Intersection 5x10 = 50, union = 100 + 100 - 50 = 150.
        assertThat(polygonIntersectionArea(a, b)).isWithin(0.01f).of(50f)
        assertThat(polygonIoU(a, b)).isWithin(1e-4f).of(1f / 3f)
    }

    @Test
    fun `edge touching squares do not overlap`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(10f, 0f, 20f, 10f)

        // The shared edge is a zero-area intersection, so IoU must be exactly 0.
        assertThat(polygonIoU(a, b)).isEqualTo(0f)
    }

    // ---------------------------------------------------------------- winding

    @Test
    fun `winding order does not change IoU for a partially overlapping pair`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(5f, 0f, 15f, 10f)
        val bReversed = b.reversed()

        val forward = polygonIoU(a, b)
        val reversed = polygonIoU(a, bReversed)

        assertThat(forward).isWithin(1e-4f).of(1f / 3f)
        assertThat(reversed).isWithin(1e-4f).of(forward)
        assertThat(reversed).isGreaterThan(0f)
    }

    @Test
    fun `reversed winding of both polygons still intersects`() {
        val a = square(0f, 0f, 10f, 10f).reversed()
        val b = square(5f, 0f, 15f, 10f).reversed()

        assertThat(polygonIoU(a, b)).isWithin(1e-4f).of(1f / 3f)
    }

    @Test
    fun `quad in TL TR BR BL order matches the same quad reversed`() {
        val forward = quad(0f, 0f, 10f, 10f)
        val reversed = Quad(
            topLeft = forward.topLeft,
            topRight = forward.bottomLeft,
            bottomRight = forward.bottomRight,
            bottomLeft = forward.topRight
        )

        val a = listOf(
            forward.topLeft.toPointF(), forward.topRight.toPointF(),
            forward.bottomRight.toPointF(), forward.bottomLeft.toPointF()
        )
        val b = listOf(
            reversed.topLeft.toPointF(), reversed.topRight.toPointF(),
            reversed.bottomRight.toPointF(), reversed.bottomLeft.toPointF()
        )

        assertThat(signedPolygonArea(a)).isGreaterThan(0f)
        assertThat(signedPolygonArea(b)).isLessThan(0f)
        assertThat(polygonIoU(a, b)).isWithin(1e-4f).of(1f)
    }

    // ---------------------------------------------------------------- degenerate input

    @Test
    fun `degenerate polygons yield zero overlap and no exception`() {
        val valid = square(0f, 0f, 10f, 10f)
        val onePoint = listOf(PointF(5f, 5f))
        val twoPoints = listOf(PointF(0f, 0f), PointF(10f, 10f))
        val collinear = listOf(PointF(0f, 0f), PointF(5f, 5f), PointF(10f, 10f))

        assertThat(convexPolygonIntersection(emptyList(), valid)).isEmpty()
        assertThat(convexPolygonIntersection(onePoint, valid)).isEmpty()
        assertThat(convexPolygonIntersection(twoPoints, valid)).isEmpty()
        assertThat(convexPolygonIntersection(collinear, valid)).isEmpty()
        assertThat(convexPolygonIntersection(valid, emptyList())).isEmpty()

        assertThat(polygonIntersectionArea(onePoint, valid)).isEqualTo(0f)
        assertThat(polygonIntersectionArea(collinear, valid)).isEqualTo(0f)
        assertThat(polygonIoU(emptyList(), valid)).isEqualTo(0f)
        assertThat(polygonIoU(twoPoints, valid)).isEqualTo(0f)
        assertThat(polygonIoU(collinear, collinear)).isEqualTo(0f)
    }

    // ---------------------------------------------------------------- rotated quad

    @Test
    fun `rotated diamond against a square has the analytically expected area`() {
        // Square 10x10 centred at (5,5); diamond (a 45-degree rotated square) centred at the
        // same point with half-diagonal 7, so its tips poke out past all four square edges.
        val squarePoly = square(0f, 0f, 10f, 10f)
        val diamond = listOf(
            PointF(5f, -2f), PointF(12f, 5f), PointF(5f, 12f), PointF(-2f, 5f)
        )

        // Diamond area = 0.5 * 14 * 14 = 98.
        assertThat(signedPolygonArea(diamond)).isWithin(0.01f).of(98f)

        // Each square corner contributes an excluded right triangle of legs 3, 3 (area 4.5),
        // because the diamond's near edges are the lines x + y = 3 and friends.
        // Intersection = 100 - 4 * 4.5 = 82.
        val intersection = polygonIntersectionArea(squarePoly, diamond)
        assertThat(intersection).isWithin(0.5f).of(82f)
        assertThat(intersection).isGreaterThan(0f)
        assertThat(intersection).isLessThan(98f)
    }

    @Test
    fun `intersection is symmetric in its arguments`() {
        val a = square(0f, 0f, 10f, 10f)
        val b = square(5f, 0f, 15f, 10f)

        assertThat(polygonIoU(a, b)).isWithin(1e-5f).of(polygonIoU(b, a))
        assertThat(polygonIntersectionArea(a, b))
            .isWithin(1e-3f).of(polygonIntersectionArea(b, a))
    }
}
