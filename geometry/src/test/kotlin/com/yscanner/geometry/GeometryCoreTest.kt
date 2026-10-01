package com.yscanner.geometry

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Test

/**
 * Tests for the geometry primitives, boundary extraction and polygon approximation.
 *
 * All of this runs on the JVM with hand-built data — no device, no OpenCV, no image assets.
 */
class GeometryCoreTest {

    // ---------------------------------------------------------------- primitives

    @Test
    fun `convex hull of a square plus an interior point is the four corners`() {
        val hull = convexHull(
            listOf(
                PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f),
                PointF(5f, 5f) // interior, must be discarded
            )
        )

        assertThat(hull).hasSize(4)
        assertThat(hull).containsExactly(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )
    }

    @Test
    fun `convex hull drops duplicate and collinear points`() {
        val withDuplicates = convexHull(
            listOf(
                PointF(0f, 0f), PointF(0f, 0f), PointF(10f, 0f),
                PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
            )
        )
        assertThat(withDuplicates).hasSize(4)

        // All-collinear input degenerates to the two extremes.
        val collinear = convexHull(listOf(PointF(0f, 0f), PointF(5f, 0f), PointF(10f, 0f)))
        assertThat(collinear).hasSize(2)
        assertThat(collinear).containsExactly(PointF(0f, 0f), PointF(10f, 0f))
    }

    @Test
    fun `convex hull returns the input for fewer than three points`() {
        assertThat(convexHull(listOf(PointF(1f, 1f)))).hasSize(1)
        assertThat(convexHull(listOf(PointF(1f, 1f), PointF(2f, 2f)))).hasSize(2)
        assertThat(convexHull(emptyList())).isEmpty()
    }

    @Test
    fun `shoelace area is positive for clockwise winding in image coordinates`() {
        // y grows downwards, so TL -> TR -> BR -> BL is visually clockwise.
        val clockwise = listOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )
        val counterClockwise = clockwise.reversed()

        assertThat(signedPolygonArea(clockwise)).isWithin(0.001f).of(100f)
        assertThat(signedPolygonArea(counterClockwise)).isWithin(0.001f).of(-100f)
        // Absolute area is winding-independent.
        assertThat(Boundary(clockwise).area()).isWithin(0.001f).of(100f)
        assertThat(Boundary(counterClockwise).area()).isWithin(0.001f).of(100f)
    }

    @Test
    fun `area and perimeter of a triangle are correct`() {
        val triangle = Boundary(listOf(PointF(0f, 0f), PointF(4f, 0f), PointF(0f, 3f)))
        assertThat(triangle.area()).isWithin(0.001f).of(6f)
        assertThat(triangle.perimeter()).isWithin(0.001f).of(12f) // 4 + 3 + 5
    }

    @Test
    fun `degenerate polygons report zero area`() {
        assertThat(signedPolygonArea(listOf(PointF(0f, 0f), PointF(1f, 1f)))).isEqualTo(0f)
        assertThat(signedPolygonArea(emptyList())).isEqualTo(0f)
        assertThat(polygonPerimeter(listOf(PointF(0f, 0f)))).isEqualTo(0f)
    }

    @Test
    fun `centroid of a square is its centre`() {
        val square = Boundary(
            listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        )
        val centroid = square.centroid()
        assertThat(centroid.x).isWithin(0.01f).of(5f)
        assertThat(centroid.y).isWithin(0.01f).of(5f)
    }

    @Test
    fun `bounding box is tight`() {
        val box = Boundary(
            listOf(PointF(3f, 7f), PointF(20f, 7f), PointF(20f, 30f), PointF(3f, 30f))
        ).boundingBox()

        assertThat(box.left).isEqualTo(3f)
        assertThat(box.top).isEqualTo(7f)
        assertThat(box.right).isEqualTo(20f)
        assertThat(box.bottom).isEqualTo(30f)
        assertThat(box.area).isWithin(0.001f).of(17f * 23f)
    }

    @Test
    fun `point in polygon classifies inside outside and edge`() {
        val square = listOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )

        assertThat(pointInPolygon(PointF(5f, 5f), square)).isTrue()
        assertThat(pointInPolygon(PointF(15f, 5f), square)).isFalse()
        assertThat(pointInPolygon(PointF(-1f, 5f), square)).isFalse()
        // On-edge points count as inside so rasterisation does not lose border pixels.
        assertThat(pointInPolygon(PointF(0f, 5f), square)).isTrue()
        assertThat(pointInPolygon(PointF(5f, 0f), square)).isTrue()
        assertThat(pointInPolygon(PointF(5f, 5f), emptyList())).isFalse()
    }

    @Test
    fun `distance to segment clamps to the endpoints`() {
        val a = PointF(0f, 0f)
        val b = PointF(10f, 0f)
        assertThat(distanceToSegment(PointF(5f, 3f), a, b)).isWithin(0.001f).of(3f)
        // Beyond b, distance is measured to b itself.
        assertThat(distanceToSegment(PointF(20f, 0f), a, b)).isWithin(0.001f).of(10f)
        // Degenerate segment falls back to point distance.
        assertThat(distanceToSegment(PointF(3f, 4f), a, a)).isWithin(0.001f).of(5f)
    }

    @Test
    fun `cross product sign indicates turn direction`() {
        val o = PointF(0f, 0f)
        assertThat(cross(o, PointF(1f, 0f), PointF(0f, 1f))).isGreaterThan(0f)
        assertThat(cross(o, PointF(0f, 1f), PointF(1f, 0f))).isLessThan(0f)
        assertThat(cross(o, PointF(1f, 0f), PointF(2f, 0f))).isEqualTo(0f)
    }

    // ---------------------------------------------------------------- BinaryMask

    @Test
    fun `binary mask is bounds safe`() {
        val mask = BinaryMask(4, 4, ByteArray(16))
        mask.set(1, 1)

        assertThat(mask.get(1, 1)).isTrue()
        assertThat(mask.get(0, 0)).isFalse()
        assertThat(mask.get(-1, 0)).isFalse()
        assertThat(mask.get(4, 0)).isFalse()
        assertThat(mask.get(0, 99)).isFalse()
        assertThat(mask.foregroundCount()).isEqualTo(1)

        // Out-of-range writes are ignored rather than throwing.
        mask.set(99, 99)
        assertThat(mask.foregroundCount()).isEqualTo(1)
    }

    @Test
    fun `mask from float array thresholds correctly`() {
        val mask = BinaryMask.fromFloatArray(
            2, 2,
            floatArrayOf(0.9f, 0.1f, 0.5f, 0.4f),
            threshold = 0.5f
        )

        assertThat(mask.get(0, 0)).isTrue()  // 0.9 >= 0.5
        assertThat(mask.get(1, 0)).isFalse() // 0.1
        assertThat(mask.get(0, 1)).isTrue()  // 0.5 >= 0.5 (inclusive)
        assertThat(mask.get(1, 1)).isFalse() // 0.4
        assertThat(mask.foregroundCount()).isEqualTo(2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mask rejects undersized data`() {
        BinaryMask(4, 4, ByteArray(15))
    }

    // ---------------------------------------------------------------- extraction

    @Test
    fun `extractor finds a single rectangle boundary`() {
        val quad = Quad(Corner(20f, 20f), Corner(100f, 20f), Corner(100f, 100f), Corner(20f, 20f + 80f))
        val mask = BinaryMask.filledQuad(140, 140, quad)

        val boundaries = BoundaryExtractor(minComponentArea = 16).extract(mask)

        assertThat(boundaries).hasSize(1)
        assertThat(boundaries[0].isClosed).isTrue()
        // A rasterised rectangle should have a convex hull of exactly four corners.
        assertThat(convexHull(boundaries[0].points)).hasSize(4)
    }

    @Test
    fun `extractor sorts multiple components by area descending`() {
        val mask = BinaryMask(120, 60, ByteArray(120 * 60))
        // Small blob: 6x6 = 36 px at (2,2)
        for (y in 2 until 8) for (x in 2 until 8) mask.set(x, y)
        // Large blob: 20x20 = 400 px at (40,20)
        for (y in 20 until 40) for (x in 40 until 60) mask.set(x, y)

        val boundaries = BoundaryExtractor(minComponentArea = 10).extract(mask)

        assertThat(boundaries).hasSize(2)
        assertThat(boundaries[0].area()).isGreaterThan(boundaries[1].area())
    }

    @Test
    fun `extractor filters components below the minimum area`() {
        val mask = BinaryMask(40, 40, ByteArray(40 * 40))
        // A 3x3 = 9 px speck.
        for (y in 5 until 8) for (x in 5 until 8) mask.set(x, y)

        assertThat(BoundaryExtractor(minComponentArea = 64).extract(mask)).isEmpty()
        assertThat(BoundaryExtractor(minComponentArea = 4).extract(mask)).hasSize(1)
    }

    @Test
    fun `extractor handles degenerate masks without throwing`() {
        assertThat(BoundaryExtractor(minComponentArea = 1).extract(BinaryMask(10, 10, ByteArray(100))))
            .isEmpty()

        val single = BinaryMask(10, 10, ByteArray(100))
        single.set(5, 5)
        assertThat(BoundaryExtractor(minComponentArea = 1).extract(single)).hasSize(1)

        val zeroSized = BinaryMask(0, 0, ByteArray(0))
        assertThat(BoundaryExtractor(minComponentArea = 1).extract(zeroSized)).isEmpty()
    }

    @Test
    fun `extractor handles a mask touching all four edges`() {
        val full = BinaryMask(20, 20, ByteArray(20 * 20) { BinaryMask.FOREGROUND })
        val boundaries = BoundaryExtractor(minComponentArea = 1).extract(full)

        assertThat(boundaries).hasSize(1)
        assertThat(boundaries[0].points).isNotEmpty()
    }

    // ---------------------------------------------------------------- approximation

    @Test
    fun `douglas peucker keeps endpoints and drops redundant points`() {
        val line = listOf(
            PointF(0f, 0f), PointF(1f, 0.1f), PointF(2f, 0f),
            PointF(3f, 0.1f), PointF(4f, 0f)
        )
        val simplified = approximate(line, epsilon = 0.5f)

        assertThat(simplified).hasSize(2)
        assertThat(simplified.first()).isEqualTo(PointF(0f, 0f))
        assertThat(simplified.last()).isEqualTo(PointF(4f, 0f))
    }

    @Test
    fun `douglas peucker returns the input for degenerate arguments`() {
        val points = listOf(PointF(0f, 0f), PointF(1f, 1f))
        assertThat(approximate(points, 1f)).isEqualTo(points)
        val line = listOf(PointF(0f, 0f), PointF(1f, 1f), PointF(2f, 2f))
        assertThat(approximate(line, epsilon = 0f)).isEqualTo(line)
    }

    @Test
    fun `approximateToVertexCount reduces a dense rasterised rectangle to four corners`() {
        val quad = Quad(Corner(20f, 20f), Corner(120f, 20f), Corner(120f, 100f), Corner(20f, 100f))
        val mask = BinaryMask.filledQuad(140, 120, quad)
        val contour = BoundaryExtractor(minComponentArea = 16).extract(mask).first().points

        // The traced staircase contour has far more than four vertices.
        assertThat(contour.size).isGreaterThan(4)

        val reduced = approximateToVertexCount(contour, target = 4)
        assertThat(reduced).hasSize(4)
    }

    @Test
    fun `approximateToVertexCount returns short inputs unchanged`() {
        val four = listOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )
        assertThat(approximateToVertexCount(four, 4)).isEqualTo(four)
    }
}
