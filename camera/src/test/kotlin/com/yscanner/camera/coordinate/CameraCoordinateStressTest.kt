package com.yscanner.camera.coordinate

import com.google.common.truth.Truth.assertThat
import com.yscanner.common.math.Matrix3x3
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Adversarial stress test suite for the 7-Space Matrix Coordinate Transformation System.
 * Tests high-iteration property invariants, boundary extremes, rotation/aspect ratio permutations,
 * matrix non-singularity, error contracts, and quad convexity/winding preservation.
 */
class CameraCoordinateStressTest {

    private val defaultQuad = Quad(
        topLeft = Corner(400f, 300f),
        topRight = Corner(3600f, 400f),
        bottomRight = Corner(3500f, 2700f),
        bottomLeft = Corner(500f, 2600f)
    )

    private fun createStandardConfig(
        sensorRotation: Int = 90,
        analysisRotation: Int = 90,
        captureRotation: Int = 90,
        analysisWidth: Int = 1280,
        analysisHeight: Int = 720,
        captureWidth: Int = 4032,
        captureHeight: Int = 3024,
        previewWidth: Int = 1080,
        previewHeight: Int = 2400,
        previewScaleType: PreviewScaleType = PreviewScaleType.FILL_CENTER,
        quad: Quad? = defaultQuad
    ): CameraCoordinateConfig = CameraCoordinateConfig(
        sensorWidth = captureWidth,
        sensorHeight = captureHeight,
        sensorRotationDegrees = sensorRotation,

        analysisWidth = analysisWidth,
        analysisHeight = analysisHeight,
        analysisRotationDegrees = analysisRotation,
        analysisCropLeft = 0,
        analysisCropTop = 0,
        analysisCropRight = analysisWidth,
        analysisCropBottom = analysisHeight,

        previewWidth = previewWidth,
        previewHeight = previewHeight,
        previewScaleType = previewScaleType,

        captureWidth = captureWidth,
        captureHeight = captureHeight,
        captureRotationDegrees = captureRotation,

        processedQuad = quad,
        processedWidth = 2480,
        processedHeight = 3508,

        pdfPageWidthPt = 595.28f,
        pdfPageHeightPt = 841.89f
    )

    // =========================================================================
    // 1. High-Iteration Property Tests (Thousands of Points)
    // =========================================================================

    @Test
    fun `high iteration round trip property invariant across all 49 space pairs`() {
        val config = createStandardConfig()
        val transformer = CameraCoordinateTransformer(config)
        val rng = Random(42)

        val spaces = CoordinateSpace.entries.toTypedArray()
        val pointsPerSpace = 1000

        var totalRoundTrips = 0
        var maxErrorObserved = 0f

        // Define bounding domains for generating realistic random points in each space
        fun samplePoint(space: CoordinateSpace): PointF = when (space) {
            CoordinateSpace.SENSOR, CoordinateSpace.IMAGE_CAPTURE ->
                PointF(rng.nextFloat() * 4032f, rng.nextFloat() * 3024f)
            CoordinateSpace.IMAGE_ANALYSIS ->
                PointF(rng.nextFloat() * 1280f, rng.nextFloat() * 720f)
            CoordinateSpace.PREVIEW_VIEW ->
                PointF(rng.nextFloat() * 1080f, rng.nextFloat() * 2400f)
            CoordinateSpace.NORMALIZED ->
                PointF(rng.nextFloat(), rng.nextFloat())
            CoordinateSpace.PROCESSED ->
                PointF(rng.nextFloat() * 2480f, rng.nextFloat() * 3508f)
            CoordinateSpace.PDF ->
                PointF(rng.nextFloat() * 595.28f, rng.nextFloat() * 841.89f)
        }

        for (fromSpace in spaces) {
            for (toSpace in spaces) {
                for (i in 0 until pointsPerSpace) {
                    val p = samplePoint(fromSpace)
                    val transformed = transformer.mapPoint(p, fromSpace, toSpace)
                    val roundTrip = transformer.mapPoint(transformed, toSpace, fromSpace)

                    val dx = abs(roundTrip.x - p.x)
                    val dy = abs(roundTrip.y - p.y)
                    val error = hypot(dx, dy)
                    if (error > maxErrorObserved) {
                        maxErrorObserved = error
                    }

                    // Tolerance: normalized is in [0..1], others are up to ~4000 pixels.
                    val tolerance = when (fromSpace) {
                        CoordinateSpace.NORMALIZED -> 1e-4f
                        CoordinateSpace.PDF -> 0.01f
                        else -> 0.08f // Sub-pixel precision on 4000x3000 buffers
                    }

                    assertThat(dx).isLessThan(tolerance)
                    assertThat(dy).isLessThan(tolerance)
                    totalRoundTrips++
                }
            }
        }

        assertThat(totalRoundTrips).isEqualTo(49 * pointsPerSpace)
        assertThat(maxErrorObserved).isLessThan(0.08f)
    }

    @Test
    fun `transitive composition property holds across all space triplets A to B to C vs A to C`() {
        val config = createStandardConfig()
        val transformer = CameraCoordinateTransformer(config)
        val rng = Random(1337)

        val spaces = CoordinateSpace.entries.toTypedArray()
        var testedTriplets = 0

        for (a in spaces) {
            for (b in spaces) {
                for (c in spaces) {
                    val matDirect = transformer.getTransformMatrix(a, c)
                    val matComposed = transformer.getTransformMatrix(b, c) * transformer.getTransformMatrix(a, b)

                    // Test with 5 random points sampled from space A's valid domain
                    for (i in 0 until 5) {
                        val p = when (a) {
                            CoordinateSpace.SENSOR, CoordinateSpace.IMAGE_CAPTURE ->
                                PointF(rng.nextFloat() * 3000f + 100f, rng.nextFloat() * 2000f + 100f)
                            CoordinateSpace.IMAGE_ANALYSIS ->
                                PointF(rng.nextFloat() * 1000f + 50f, rng.nextFloat() * 600f + 50f)
                            CoordinateSpace.PREVIEW_VIEW ->
                                PointF(rng.nextFloat() * 900f + 50f, rng.nextFloat() * 1800f + 50f)
                            CoordinateSpace.NORMALIZED ->
                                PointF(rng.nextFloat() * 0.8f + 0.1f, rng.nextFloat() * 0.8f + 0.1f)
                            CoordinateSpace.PROCESSED ->
                                PointF(rng.nextFloat() * 2000f + 100f, rng.nextFloat() * 3000f + 100f)
                            CoordinateSpace.PDF ->
                                PointF(rng.nextFloat() * 500f + 50f, rng.nextFloat() * 700f + 50f)
                        }

                        val (d1X, d1Y) = matDirect.mapPoint(p.x, p.y)
                        val (c1X, c1Y) = matComposed.mapPoint(p.x, p.y)

                        // Composition tolerance: normalized space tolerance vs pixel space
                        val tol = when (c) {
                            CoordinateSpace.NORMALIZED -> 1e-4f
                            CoordinateSpace.PDF -> 0.02f
                            else -> 0.1f
                        }

                        assertThat(abs(d1X - c1X)).isLessThan(tol)
                        assertThat(abs(d1Y - c1Y)).isLessThan(tol)
                    }
                    testedTriplets++
                }
            }
        }

        assertThat(testedTriplets).isEqualTo(7 * 7 * 7)
    }

    // =========================================================================
    // 2. Boundary and Extremal Stress Tests
    // =========================================================================

    @Test
    fun `boundary points at origin, corners, negatives, and large values round trip cleanly`() {
        val config = createStandardConfig()
        val transformer = CameraCoordinateTransformer(config)

        val adversarialPoints = listOf(
            PointF(0f, 0f),
            PointF(1f, 1f),
            PointF(-1f, -1f),
            PointF(0f, 1f),
            PointF(1f, 0f),
            PointF(-5000f, -5000f),
            PointF(1e5f, 1e5f),
            PointF(-1e5f, -1e5f),
            PointF(1e-5f, 1e-5f),
            PointF(-1e-5f, 1e-5f)
        )

        for (p in adversarialPoints) {
            for (space in CoordinateSpace.entries) {
                val norm = transformer.mapPoint(p, space, CoordinateSpace.NORMALIZED)
                val roundTrip = transformer.mapPoint(norm, CoordinateSpace.NORMALIZED, space)

                // Relative error check for large coordinates, absolute for near-zero
                val diffX = abs(roundTrip.x - p.x)
                val diffY = abs(roundTrip.y - p.y)
                val denomX = maxOf(abs(p.x), 1.0f)
                val denomY = maxOf(abs(p.y), 1.0f)

                assertThat(diffX / denomX).isLessThan(3e-4f)
                assertThat(diffY / denomY).isLessThan(3e-4f)
            }
        }
    }

    @Test
    fun `extreme sensor and preview dimensions maintain numerical stability`() {
        // 1x1 minimal dimension
        val tinyConfig = createStandardConfig(
            captureWidth = 1,
            captureHeight = 1,
            analysisWidth = 1,
            analysisHeight = 1,
            previewWidth = 1,
            previewHeight = 1,
            quad = null
        )
        val tinyTransformer = CameraCoordinateTransformer(tinyConfig)
        val p = PointF(0.5f, 0.5f)
        val tinyNorm = tinyTransformer.mapPoint(p, CoordinateSpace.IMAGE_CAPTURE, CoordinateSpace.NORMALIZED)
        val tinyBack = tinyTransformer.mapPoint(tinyNorm, CoordinateSpace.NORMALIZED, CoordinateSpace.IMAGE_CAPTURE)
        assertThat(tinyBack.x).isWithin(1e-5f).of(p.x)
        assertThat(tinyBack.y).isWithin(1e-5f).of(p.y)

        // 100,000 x 100,000 gigantic dimension
        val hugeConfig = createStandardConfig(
            captureWidth = 100_000,
            captureHeight = 100_000,
            previewWidth = 100_000,
            previewHeight = 100_000,
            quad = null
        )
        val hugeTransformer = CameraCoordinateTransformer(hugeConfig)
        val hugePoint = PointF(50_000f, 50_000f)
        val hugeNorm = hugeTransformer.mapPoint(hugePoint, CoordinateSpace.IMAGE_CAPTURE, CoordinateSpace.NORMALIZED)
        assertThat(hugeNorm.x).isWithin(1e-4f).of(0.5f)
        assertThat(hugeNorm.y).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `degenerate analysis crop bounds are handled gracefully via coerceAtLeast`() {
        val config = CameraCoordinateConfig(
            analysisWidth = 1280,
            analysisHeight = 720,
            analysisCropLeft = 100,
            analysisCropTop = 100,
            analysisCropRight = 100, // zero width crop
            analysisCropBottom = 100 // zero height crop
        )
        val transformer = CameraCoordinateTransformer(config)
        val p = PointF(100f, 100f)
        val norm = transformer.mapPoint(p, CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.NORMALIZED)
        assertThat(norm.x.isFinite()).isTrue()
        assertThat(norm.y.isFinite()).isTrue()
    }

    @Test
    fun `unconfigured spaces throw IllegalStateException when processedQuad is null`() {
        val unconfiguredConfig = createStandardConfig(quad = null)
        val transformer = CameraCoordinateTransformer(unconfiguredConfig)

        // Transforming from unconfigured space throws IllegalStateException
        val ex1 = assertThrows(IllegalStateException::class.java) {
            transformer.mapPoint(PointF(100f, 100f), CoordinateSpace.PROCESSED, CoordinateSpace.NORMALIZED)
        }
        assertThat(ex1.message).contains("processedQuad")

        val ex2 = assertThrows(IllegalStateException::class.java) {
            transformer.mapPoint(PointF(100f, 100f), CoordinateSpace.NORMALIZED, CoordinateSpace.PDF)
        }
        assertThat(ex2.message).contains("processedQuad")

        // Identity transform on the same unconfigured space succeeds without error
        val p = PointF(42f, 99f)
        val sameProc = transformer.mapPoint(p, CoordinateSpace.PROCESSED, CoordinateSpace.PROCESSED)
        assertThat(sameProc.x).isEqualTo(p.x)
        assertThat(sameProc.y).isEqualTo(p.y)
    }

    // =========================================================================
    // 3. Rotations and Aspect-Ratio Stress Permutations
    // =========================================================================

    @Test
    fun `all 64 rotation permutations preserve center alignment and invertibility`() {
        val rotationAngles = listOf(0, 90, 180, 270)
        var testedCombinations = 0

        for (sensorRot in rotationAngles) {
            for (analysisRot in rotationAngles) {
                for (captureRot in rotationAngles) {
                    val config = createStandardConfig(
                        sensorRotation = sensorRot,
                        analysisRotation = analysisRot,
                        captureRotation = captureRot,
                        analysisWidth = 1280,
                        analysisHeight = 720,
                        captureWidth = 4032,
                        captureHeight = 3024,
                        quad = null
                    )
                    val transformer = CameraCoordinateTransformer(config)

                    // 1. Center of analysis (640, 360) must map to capture center (2016, 1512)
                    val aCenter = PointF(640f, 360f)
                    val cFromA = transformer.mapPoint(aCenter, CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.IMAGE_CAPTURE)
                    assertThat(cFromA.x).isWithin(0.1f).of(2016f)
                    assertThat(cFromA.y).isWithin(0.1f).of(1512f)

                    // 2. Center of sensor (2016, 1512) must map to normalized center (0.5, 0.5)
                    val sCenter = PointF(2016f, 1512f)
                    val normFromS = transformer.mapPoint(sCenter, CoordinateSpace.SENSOR, CoordinateSpace.NORMALIZED)
                    assertThat(normFromS.x).isWithin(1e-4f).of(0.5f)
                    assertThat(normFromS.y).isWithin(1e-4f).of(0.5f)

                    // 3. Round-trip between analysis and capture
                    val aRoundTrip = transformer.mapPoint(cFromA, CoordinateSpace.IMAGE_CAPTURE, CoordinateSpace.IMAGE_ANALYSIS)
                    assertThat(aRoundTrip.x).isWithin(0.1f).of(aCenter.x)
                    assertThat(aRoundTrip.y).isWithin(0.1f).of(aCenter.y)

                    testedCombinations++
                }
            }
        }

        assertThat(testedCombinations).isEqualTo(64)
    }

    @Test
    fun `aspect ratio permutations 1-1, 4-3, 16-9, 19_5-9, 21-9 maintain FOV alignment`() {
        val aspectRatios = listOf(
            "1:1" to (1080 to 1080),
            "4:3" to (1440 to 1080),
            "16:9" to (1920 to 1080),
            "19.5:9" to (2340 to 1080),
            "21:9" to (2520 to 1080)
        )

        val scaleTypes = listOf(PreviewScaleType.FILL_CENTER, PreviewScaleType.FIT_CENTER)

        for ((_, analysisDim) in aspectRatios) {
            for ((_, captureDim) in aspectRatios) {
                for (scaleType in scaleTypes) {
                    val config = createStandardConfig(
                        analysisWidth = analysisDim.first,
                        analysisHeight = analysisDim.second,
                        captureWidth = captureDim.first,
                        captureHeight = captureDim.second,
                        previewScaleType = scaleType,
                        quad = null
                    )
                    val transformer = CameraCoordinateTransformer(config)

                    // Center of analysis buffer
                    val aCenter = PointF(analysisDim.first / 2f, analysisDim.second / 2f)
                    val cCenter = transformer.mapPoint(aCenter, CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.IMAGE_CAPTURE)

                    // Center of capture buffer
                    val expectedCx = captureDim.first / 2f
                    val expectedCy = captureDim.second / 2f

                    assertThat(cCenter.x).isWithin(0.1f).of(expectedCx)
                    assertThat(cCenter.y).isWithin(0.1f).of(expectedCy)

                    // Center of preview view
                    val pCenter = PointF(config.previewWidth / 2f, config.previewHeight / 2f)
                    val normCenter = transformer.mapPoint(pCenter, CoordinateSpace.PREVIEW_VIEW, CoordinateSpace.NORMALIZED)
                    assertThat(normCenter.x).isWithin(1e-4f).of(0.5f)
                    assertThat(normCenter.y).isWithin(1e-4f).of(0.5f)
                }
            }
        }
    }

    @Test
    fun `negative and non-standard rotation angles normalize consistently modulo 360`() {
        val angles = listOf(-90, -180, -270, 360, 450, 720)

        for (deg in angles) {
            val m = Matrix3x3.rotationUnitSquare(deg)
            val (cx, cy) = m.mapPoint(0.5f, 0.5f)
            assertThat(cx).isWithin(1e-4f).of(0.5f)
            assertThat(cy).isWithin(1e-4f).of(0.5f)

            // Inverse should equal opposite rotation
            val inv = m.invert()
            assertThat(inv).isNotNull()
            val (testBackX, testBackY) = inv!!.mapPoint(cx, cy)
            assertThat(testBackX).isWithin(1e-4f).of(0.5f)
            assertThat(testBackY).isWithin(1e-4f).of(0.5f)
        }
    }

    // =========================================================================
    // 4. Quad Mapping and Convexity Invariants
    // =========================================================================

    @Test
    fun `random strictly convex quads remain strictly convex across all spaces without vertex inversion`() {
        val config = createStandardConfig()
        val transformer = CameraCoordinateTransformer(config)
        val rng = Random(888)

        // Generate 200 random strictly convex quadrilaterals in analysis space
        for (i in 0 until 200) {
            val cx = 640f + (rng.nextFloat() - 0.5f) * 200f
            val cy = 360f + (rng.nextFloat() - 0.5f) * 150f
            val rx = 200f + rng.nextFloat() * 150f
            val ry = 150f + rng.nextFloat() * 100f

            // Clockwise vertex ordering: Top-Left, Top-Right, Bottom-Right, Bottom-Left
            val p0 = PointF(cx - rx * (0.6f + rng.nextFloat() * 0.3f), cy - ry * (0.6f + rng.nextFloat() * 0.3f))
            val p1 = PointF(cx + rx * (0.6f + rng.nextFloat() * 0.3f), cy - ry * (0.6f + rng.nextFloat() * 0.3f))
            val p2 = PointF(cx + rx * (0.6f + rng.nextFloat() * 0.3f), cy + ry * (0.6f + rng.nextFloat() * 0.3f))
            val p3 = PointF(cx - rx * (0.6f + rng.nextFloat() * 0.3f), cy + ry * (0.6f + rng.nextFloat() * 0.3f))

            val sourceQuad = Quad(
                topLeft = Corner.fromPointF(p0),
                topRight = Corner.fromPointF(p1),
                bottomRight = Corner.fromPointF(p2),
                bottomLeft = Corner.fromPointF(p3)
            )

            assertThat(sourceQuad.isConvex()).isTrue()
            assertThat(sourceQuad.area()).isGreaterThan(100f)

            // Map across spaces
            val targetSpaces = listOf(
                CoordinateSpace.SENSOR,
                CoordinateSpace.PREVIEW_VIEW,
                CoordinateSpace.IMAGE_CAPTURE,
                CoordinateSpace.NORMALIZED,
                CoordinateSpace.PROCESSED,
                CoordinateSpace.PDF
            )

            for (targetSpace in targetSpaces) {
                val mappedQuad = transformer.mapQuad(sourceQuad, CoordinateSpace.IMAGE_ANALYSIS, targetSpace)

                // 1. Must remain strictly convex
                assertThat(mappedQuad.isConvex()).isTrue()

                // 2. Area must be strictly non-zero
                assertThat(mappedQuad.area()).isGreaterThan(0.001f)

                // 3. Round-trip mapping back to ANALYSIS must match original vertices without swapping
                val roundTripQuad = transformer.mapQuad(mappedQuad, targetSpace, CoordinateSpace.IMAGE_ANALYSIS)

                assertThat(roundTripQuad.topLeft.x).isWithin(0.1f).of(sourceQuad.topLeft.x)
                assertThat(roundTripQuad.topLeft.y).isWithin(0.1f).of(sourceQuad.topLeft.y)

                assertThat(roundTripQuad.topRight.x).isWithin(0.1f).of(sourceQuad.topRight.x)
                assertThat(roundTripQuad.topRight.y).isWithin(0.1f).of(sourceQuad.topRight.y)

                assertThat(roundTripQuad.bottomRight.x).isWithin(0.1f).of(sourceQuad.bottomRight.x)
                assertThat(roundTripQuad.bottomRight.y).isWithin(0.1f).of(sourceQuad.bottomRight.y)

                assertThat(roundTripQuad.bottomLeft.x).isWithin(0.1f).of(sourceQuad.bottomLeft.x)
                assertThat(roundTripQuad.bottomLeft.y).isWithin(0.1f).of(sourceQuad.bottomLeft.y)
            }
        }
    }

    @Test
    fun `homography with degenerate and edge cases does not crash or produce NaNs`() {
        // Case 1: Parallelogram (deltaX3 = 0, deltaY3 = 0 triggers affine optimization branch)
        val hAffine = Matrix3x3.homographyFromQuadToRect(
            0f, 0f,
            100f, 0f,
            120f, 80f,
            20f, 80f,
            500f, 400f
        )
        val (p0x, p0y) = hAffine.mapPoint(0f, 0f)
        assertThat(p0x).isWithin(1e-3f).of(0f)
        assertThat(p0y).isWithin(1e-3f).of(0f)

        // Case 2: Three collinear points (denom = 0 triggers degenerate fallback branch)
        val hCollinear3 = Matrix3x3.homographyFromQuadToRect(
            0f, 0f,
            50f, 50f,
            100f, 100f,
            0f, 100f,
            500f, 400f
        )
        val (c3x, c3y) = hCollinear3.mapPoint(0f, 0f)
        assertThat(c3x.isFinite()).isTrue()
        assertThat(c3y.isFinite()).isTrue()

        // Case 3: Four identical points (complete point degeneracy)
        val hPoint = Matrix3x3.homographyFromQuadToRect(
            50f, 50f,
            50f, 50f,
            50f, 50f,
            50f, 50f,
            500f, 400f
        )
        val (ptX, ptY) = hPoint.mapPoint(50f, 50f)
        assertThat(ptX.isFinite()).isTrue()
        assertThat(ptY.isFinite()).isTrue()
    }
}
