package com.yscanner.geometry

import com.google.common.truth.Truth.assertThat
import com.yscanner.domain.model.PointF
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tests for the total-least-squares line fit and line intersection used by M08 corner refinement.
 *
 * The fit feeds directly into corner positions on a 12 MP capture, so the assertions here are about
 * accuracy (a degree, a pixel), not merely about not crashing.
 */
class LineFitTest {

    /** `count` noise-free points along a line through the origin at [angleDeg]. */
    private fun collinear(angleDeg: Double, count: Int = 24, step: Double = 6.0): List<PointF> {
        val a = Math.toRadians(angleDeg)
        val dx = cos(a)
        val dy = sin(a)
        return (0 until count).map { i ->
            PointF((dx * step * i).toFloat(), (dy * step * i).toFloat())
        }
    }

    private fun unit(angleDeg: Double): PointF {
        val a = Math.toRadians(angleDeg)
        return PointF(cos(a).toFloat(), sin(a).toFloat())
    }

    /** Smallest angle between [line]'s direction and the [angleDeg] axis, sign-agnostic, in degrees. */
    private fun directionErrorDegrees(line: FittedLine, angleDeg: Double): Double {
        val expected = unit(angleDeg)
        val d = line.direction
        val dot = abs((d.x * expected.x + d.y * expected.y).toDouble())
        val norm = hypot(d.x.toDouble(), d.y.toDouble()) * hypot(expected.x.toDouble(), expected.y.toDouble())
        return Math.toDegrees(acos((dot / norm).coerceIn(-1.0, 1.0)))
    }

    /** Perpendicular distance from [point] to the line through the origin with unit direction [d]. */
    private fun distanceToOriginLine(point: PointF, d: PointF): Double =
        abs(d.x * point.y - d.y * point.x).toDouble()

    // ------------------------------------------------------------ exact recovery

    @Test
    fun `recovers a horizontal line exactly`() {
        val line = requireNotNull(fitLineTotalLeastSquares(collinear(0.0)))

        assertThat(directionErrorDegrees(line, 0.0)).isLessThan(1e-3)
        // Centroid of the sample run 0..138 sits at x = 69, y = 0.
        assertThat(line.point.x).isWithin(0.5f).of(69f)
        assertThat(abs(line.point.y)).isLessThan(1e-3f)
    }

    @Test
    fun `recovers a vertical line exactly`() {
        val line = requireNotNull(fitLineTotalLeastSquares(collinear(90.0)))

        assertThat(directionErrorDegrees(line, 90.0)).isLessThan(1e-3)
        assertThat(abs(line.point.x)).isLessThan(1e-3f)
        assertThat(line.point.y).isWithin(0.5f).of(69f)
    }

    @Test
    fun `recovers a 30 degree line exactly`() {
        val line = requireNotNull(fitLineTotalLeastSquares(collinear(30.0)))

        assertThat(directionErrorDegrees(line, 30.0)).isLessThan(1e-3)
        assertThat(distanceToOriginLine(line.point, unit(30.0))).isLessThan(1e-3)
    }

    // ------------------------------------------------------------ robustness

    @Test
    fun `fits a noisy 30 degree line within a degree and a pixel`() {
        val random = Random(7)
        val d = unit(30.0)
        val normal = PointF(-d.y, d.x)
        val points = (0 until 40).map { i ->
            val along = 6.0 * i
            val offset = (random.nextDouble() - 0.5) * 0.6 // +-0.3 px perpendicular noise
            PointF(
                (d.x * along + normal.x * offset).toFloat(),
                (d.y * along + normal.y * offset).toFloat()
            )
        }

        val line = requireNotNull(fitLineTotalLeastSquares(points))

        assertThat(directionErrorDegrees(line, 30.0)).isLessThan(1.0)
        assertThat(distanceToOriginLine(line.point, d)).isLessThan(2.0)
    }

    @Test
    fun `fits a noisy horizontal line within a degree and a pixel`() {
        val random = Random(11)
        val d = unit(0.0)
        val normal = PointF(-d.y, d.x)
        val points = (0 until 40).map { i ->
            val along = 6.0 * i
            val offset = (random.nextDouble() - 0.5) * 0.6
            PointF(
                (d.x * along + normal.x * offset).toFloat(),
                (d.y * along + normal.y * offset).toFloat()
            )
        }

        val line = requireNotNull(fitLineTotalLeastSquares(points))

        assertThat(directionErrorDegrees(line, 0.0)).isLessThan(1.0)
        assertThat(abs(line.point.y)).isLessThan(1.0f)
    }

    // ------------------------------------------------------------ degenerate fits

    @Test
    fun `returns null for fewer than two points or a coincident cloud`() {
        assertThat(fitLineTotalLeastSquares(emptyList())).isNull()
        assertThat(fitLineTotalLeastSquares(listOf(PointF(1f, 2f)))).isNull()
        assertThat(fitLineTotalLeastSquares(List(5) { PointF(3f, 3f) })).isNull()
    }

    // ------------------------------------------------------------ intersection

    @Test
    fun `intersects two crossing lines at the known point`() {
        val horizontal = FittedLine(PointF(0f, 0f), PointF(1f, 0f))
        val vertical = FittedLine(PointF(3f, -1f), PointF(0f, 1f))

        val point = requireNotNull(intersectLines(horizontal, vertical))

        assertThat(point.x).isWithin(1e-3f).of(3f)
        assertThat(point.y).isWithin(1e-3f).of(0f)
    }

    @Test
    fun `intersects two oblique lines at the known point`() {
        // y = x and y = -x + 4 meet at (2, 2).
        val rising = FittedLine(PointF(0f, 0f), PointF(1f, 1f))
        val falling = FittedLine(PointF(4f, 0f), PointF(1f, -1f))

        val point = requireNotNull(intersectLines(rising, falling))

        assertThat(point.x).isWithin(1e-3f).of(2f)
        assertThat(point.y).isWithin(1e-3f).of(2f)
    }

    @Test
    fun `returns null for parallel and identical lines`() {
        val reference = FittedLine(PointF(0f, 0f), PointF(1f, 0f))

        // Parallel, offset: no intersection.
        assertThat(intersectLines(reference, FittedLine(PointF(0f, 5f), PointF(1f, 0f)))).isNull()
        // Same direction and same point: coincident lines, still no unique intersection.
        assertThat(intersectLines(reference, FittedLine(PointF(0f, 0f), PointF(3f, 0f)))).isNull()
    }
}
