package com.localscan.camera.coordinate

import com.google.common.truth.Truth.assertThat
import com.localscan.domain.model.Corner
import com.localscan.domain.model.PointF
import com.localscan.domain.model.Quad
import org.junit.Test

class CameraCoordinateTransformerTest {

    @Test
    fun `normalized round trip succeeds across all standard spaces`() {
        val testQuad = Quad(
            topLeft = Corner(400f, 300f),
            topRight = Corner(3600f, 400f),
            bottomRight = Corner(3500f, 2700f),
            bottomLeft = Corner(500f, 2600f)
        )

        val config = CameraCoordinateConfig(
            sensorWidth = 4032,
            sensorHeight = 3024,
            sensorRotationDegrees = 90,

            analysisWidth = 1280,
            analysisHeight = 720,
            analysisRotationDegrees = 90,

            previewWidth = 1080,
            previewHeight = 2400,
            previewScaleType = PreviewScaleType.FILL_CENTER,

            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 90,

            processedQuad = testQuad,
            processedWidth = 2480,
            processedHeight = 3508,

            pdfPageWidthPt = 595.28f,
            pdfPageHeightPt = 841.89f
        )

        val transformer = CameraCoordinateTransformer(config)

        val spacesToTest = listOf(
            CoordinateSpace.SENSOR to listOf(
                PointF(0f, 0f), PointF(4032f, 0f), PointF(2016f, 1512f), PointF(4032f, 3024f), PointF(0f, 3024f)
            ),
            CoordinateSpace.IMAGE_CAPTURE to listOf(
                PointF(100f, 100f), PointF(3900f, 200f), PointF(2016f, 1512f), PointF(3800f, 2900f), PointF(200f, 2800f)
            ),
            CoordinateSpace.IMAGE_ANALYSIS to listOf(
                PointF(10f, 10f), PointF(1200f, 50f), PointF(640f, 360f), PointF(1250f, 700f), PointF(50f, 680f)
            ),
            CoordinateSpace.PREVIEW_VIEW to listOf(
                PointF(50f, 50f), PointF(1000f, 100f), PointF(540f, 1200f), PointF(1000f, 2300f), PointF(50f, 2300f)
            ),
            CoordinateSpace.PROCESSED to listOf(
                PointF(0f, 0f), PointF(2480f, 0f), PointF(1240f, 1754f), PointF(2480f, 3508f), PointF(0f, 3508f)
            ),
            CoordinateSpace.PDF to listOf(
                PointF(10f, 10f), PointF(580f, 10f), PointF(297.64f, 420.95f), PointF(580f, 830f), PointF(10f, 830f)
            )
        )

        for ((space, points) in spacesToTest) {
            for (p in points) {
                val norm = transformer.mapPoint(p, space, CoordinateSpace.NORMALIZED)
                val roundTripped = transformer.mapPoint(norm, CoordinateSpace.NORMALIZED, space)

                assertThat(roundTripped.x).isWithin(0.05f).of(p.x)
                assertThat(roundTripped.y).isWithin(0.05f).of(p.y)
            }
        }
    }

    @Test
    fun `rotations 0, 90, 180, and 270 degrees transform sensor to normalized accurately`() {
        val rotations = listOf(0, 90, 180, 270)

        for (rot in rotations) {
            val config = CameraCoordinateConfig(
                sensorWidth = 1000,
                sensorHeight = 800,
                sensorRotationDegrees = rot,
                captureWidth = 1000,
                captureHeight = 800,
                captureRotationDegrees = rot
            )
            val transformer = CameraCoordinateTransformer(config)

            // Center of sensor (500, 400) should always map to normalized center (0.5, 0.5)
            val center = PointF(500f, 400f)
            val normCenter = transformer.mapPoint(center, CoordinateSpace.SENSOR, CoordinateSpace.NORMALIZED)
            assertThat(normCenter.x).isWithin(1e-4f).of(0.5f)
            assertThat(normCenter.y).isWithin(1e-4f).of(0.5f)

            // Test top-left (0,0) based on rotation:
            val origin = PointF(0f, 0f)
            val normOrigin = transformer.mapPoint(origin, CoordinateSpace.SENSOR, CoordinateSpace.NORMALIZED)
            when (rot) {
                0 -> {
                    assertThat(normOrigin.x).isWithin(1e-4f).of(0f)
                    assertThat(normOrigin.y).isWithin(1e-4f).of(0f)
                }
                90 -> {
                    // Clockwise 90: (0,0) goes to top-right (1,0) in normalized space
                    assertThat(normOrigin.x).isWithin(1e-4f).of(1f)
                    assertThat(normOrigin.y).isWithin(1e-4f).of(0f)
                }
                180 -> {
                    // 180: (0,0) goes to bottom-right (1,1) in normalized space
                    assertThat(normOrigin.x).isWithin(1e-4f).of(1f)
                    assertThat(normOrigin.y).isWithin(1e-4f).of(1f)
                }
                270 -> {
                    // Clockwise 270: (0,0) goes to bottom-left (0,1) in normalized space
                    assertThat(normOrigin.x).isWithin(1e-4f).of(0f)
                    assertThat(normOrigin.y).isWithin(1e-4f).of(1f)
                }
            }
        }
    }

    @Test
    fun `aspect ratio mismatch correctly aligns centers between 16-9 analysis and 4-3 capture`() {
        val config = CameraCoordinateConfig(
            analysisWidth = 1280,
            analysisHeight = 720,
            analysisRotationDegrees = 90, // 720x1280 (9:16 portrait)

            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 90 // 3024x4032 (3:4 portrait)
        )
        val transformer = CameraCoordinateTransformer(config)

        // Center of analysis (640, 360)
        val analysisCenter = PointF(640f, 360f)
        val captureCenter = transformer.mapPoint(
            analysisCenter,
            CoordinateSpace.IMAGE_ANALYSIS,
            CoordinateSpace.IMAGE_CAPTURE
        )

        // Capture buffer is 4032x3024, center is (2016, 1512)
        assertThat(captureCenter.x).isWithin(0.1f).of(2016f)
        assertThat(captureCenter.y).isWithin(0.1f).of(1512f)
    }

    @Test
    fun `preview view scale types fill center and fit center behave correctly`() {
        val configFill = CameraCoordinateConfig(
            previewWidth = 1080,
            previewHeight = 2400,
            previewScaleType = PreviewScaleType.FILL_CENTER,
            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 90 // Upright scene: 3024 x 4032
        )
        val transFill = CameraCoordinateTransformer(configFill)

        // Center of preview view (540, 1200) should map to normalized center (0.5, 0.5)
        val screenCenter = PointF(540f, 1200f)
        val normFill = transFill.mapPoint(screenCenter, CoordinateSpace.PREVIEW_VIEW, CoordinateSpace.NORMALIZED)
        assertThat(normFill.x).isWithin(1e-4f).of(0.5f)
        assertThat(normFill.y).isWithin(1e-4f).of(0.5f)

        val configFit = CameraCoordinateConfig(
            previewWidth = 1080,
            previewHeight = 2400,
            previewScaleType = PreviewScaleType.FIT_CENTER,
            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 90
        )
        val transFit = CameraCoordinateTransformer(configFit)
        val normFit = transFit.mapPoint(screenCenter, CoordinateSpace.PREVIEW_VIEW, CoordinateSpace.NORMALIZED)
        assertThat(normFit.x).isWithin(1e-4f).of(0.5f)
        assertThat(normFit.y).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `homography rectification maps capture quad corners to exact processed rectangle`() {
        val p0 = PointF(300f, 200f)
        val p1 = PointF(3700f, 350f)
        val p2 = PointF(3800f, 2800f)
        val p3 = PointF(400f, 2700f)

        val inputQuad = Quad(
            topLeft = Corner.fromPointF(p0),
            topRight = Corner.fromPointF(p1),
            bottomRight = Corner.fromPointF(p2),
            bottomLeft = Corner.fromPointF(p3)
        )

        val procW = 2480
        val procH = 3508

        val config = CameraCoordinateConfig(
            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 0,
            processedQuad = inputQuad,
            processedWidth = procW,
            processedHeight = procH
        )

        val transformer = CameraCoordinateTransformer(config)

        val mappedQuad = transformer.mapQuad(inputQuad, CoordinateSpace.IMAGE_CAPTURE, CoordinateSpace.PROCESSED)

        assertThat(mappedQuad.topLeft.x).isWithin(1e-2f).of(0f)
        assertThat(mappedQuad.topLeft.y).isWithin(1e-2f).of(0f)

        assertThat(mappedQuad.topRight.x).isWithin(1e-2f).of(procW.toFloat())
        assertThat(mappedQuad.topRight.y).isWithin(1e-2f).of(0f)

        assertThat(mappedQuad.bottomRight.x).isWithin(1e-2f).of(procW.toFloat())
        assertThat(mappedQuad.bottomRight.y).isWithin(1e-2f).of(procH.toFloat())

        assertThat(mappedQuad.bottomLeft.x).isWithin(1e-2f).of(0f)
        assertThat(mappedQuad.bottomLeft.y).isWithin(1e-2f).of(procH.toFloat())

        // Map backwards from PROCESSED rectangle to IMAGE_CAPTURE
        val backQuad = transformer.mapQuad(mappedQuad, CoordinateSpace.PROCESSED, CoordinateSpace.IMAGE_CAPTURE)

        assertThat(backQuad.topLeft.x).isWithin(0.1f).of(p0.x)
        assertThat(backQuad.topLeft.y).isWithin(0.1f).of(p0.y)

        assertThat(backQuad.topRight.x).isWithin(0.1f).of(p1.x)
        assertThat(backQuad.topRight.y).isWithin(0.1f).of(p1.y)

        assertThat(backQuad.bottomRight.x).isWithin(0.1f).of(p2.x)
        assertThat(backQuad.bottomRight.y).isWithin(0.1f).of(p2.y)

        assertThat(backQuad.bottomLeft.x).isWithin(0.1f).of(p3.x)
        assertThat(backQuad.bottomLeft.y).isWithin(0.1f).of(p3.y)
    }

    @Test
    fun `quad convexity is preserved when mapped across spaces`() {
        val convexQuad = Quad(
            topLeft = Corner(200f, 200f),
            topRight = Corner(1000f, 150f),
            bottomRight = Corner(1050f, 600f),
            bottomLeft = Corner(180f, 550f)
        )
        assertThat(convexQuad.isConvex()).isTrue()

        val config = CameraCoordinateConfig(
            analysisWidth = 1280,
            analysisHeight = 720,
            analysisRotationDegrees = 90,
            captureWidth = 4032,
            captureHeight = 3024,
            captureRotationDegrees = 90
        )
        val transformer = CameraCoordinateTransformer(config)

        val captureQuad = transformer.mapQuad(convexQuad, CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.IMAGE_CAPTURE)
        assertThat(captureQuad.isConvex()).isTrue()

        val previewQuad = transformer.mapQuad(convexQuad, CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.PREVIEW_VIEW)
        assertThat(previewQuad.isConvex()).isTrue()
    }
}
