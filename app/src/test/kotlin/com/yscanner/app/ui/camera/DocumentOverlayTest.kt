package com.yscanner.app.ui.camera

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.yscanner.detection.select.TrackingState
import com.yscanner.detection.tracking.SmoothedTarget
import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad
import com.yscanner.geometry.CoordinateSpace
import com.yscanner.geometry.CoordinateTransformer
import org.junit.Test

/**
 * Unit tests for the pure half of [DocumentOverlay]. No device or emulator is required: the drawing
 * itself is not exercised here, only [overlayStyleFor] and [previewCorners].
 */
class DocumentOverlayTest {

    /**
     * A deliberately trivial transformer: it ignores the coordinate spaces and applies
     * `p * scale + offset`. Constructing a real `CameraCoordinateTransformer` would drag in a full
     * camera configuration, which is irrelevant to what these tests assert.
     */
    private class FakeTransformer(
        private val scale: Float = 1f,
        private val offset: PointF = PointF(0f, 0f)
    ) : CoordinateTransformer {

        var lastFrom: CoordinateSpace? = null
        var lastTo: CoordinateSpace? = null

        override fun mapPoint(point: PointF, from: CoordinateSpace, to: CoordinateSpace): PointF {
            lastFrom = from
            lastTo = to
            return PointF(point.x * scale + offset.x, point.y * scale + offset.y)
        }

        override fun mapQuad(quad: Quad, from: CoordinateSpace, to: CoordinateSpace): Quad {
            lastFrom = from
            lastTo = to
            return Quad(
                topLeft = mapCorner(quad.topLeft),
                topRight = mapCorner(quad.topRight),
                bottomRight = mapCorner(quad.bottomRight),
                bottomLeft = mapCorner(quad.bottomLeft)
            )
        }

        private fun mapCorner(corner: Corner): Corner {
            val mapped = mapPoint(corner.toPointF(), CoordinateSpace.IMAGE_ANALYSIS, CoordinateSpace.PREVIEW_VIEW)
            return Corner(mapped.x, mapped.y, corner.confidence)
        }
    }

    private fun targetWith(quad: Quad): SmoothedTarget = SmoothedTarget(
        quad = quad,
        confidence = 0.9f,
        trackingState = TrackingState.TRACKING,
        stableFrameCount = 4,
        isReadyForCapture = false
    )

    private fun rectQuad(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Quad = Quad(
        topLeft = Corner(left, top),
        topRight = Corner(right, top),
        bottomRight = Corner(right, bottom),
        bottomLeft = Corner(left, bottom)
    )

    @Test
    fun `overlayStyleFor returns a distinct colour for each state`() {
        val colours = TrackingState.values().map { overlayStyleFor(it).argb }
        assertThat(colours.toSet()).hasSize(TrackingState.values().size)
    }

    @Test
    fun `lost is non-opaque while tracking and stable are opaque`() {
        val lostAlpha = (overlayStyleFor(TrackingState.LOST).argb ushr 24) and 0xFF
        assertThat(lostAlpha).isLessThan(255L)

        val opaqueStates = listOf(TrackingState.TRACKING, TrackingState.STABLE)
        for (state in opaqueStates) {
            val alpha = (overlayStyleFor(state).argb ushr 24) and 0xFF
            assertThat(alpha).isEqualTo(255L)
        }
    }

    @Test
    fun `every state keeps its stroke width within the 2 to 4 dp range`() {
        for (state in TrackingState.values()) {
            val width = overlayStyleFor(state).strokeWidthDp
            assertThat(width).isAtLeast(2f)
            assertThat(width).isAtMost(4f)
        }
    }

    @Test
    fun `previewCorners returns four corners clockwise from top-left`() {
        val target = targetWith(rectQuad(0f, 0f, 10f, 5f))
        val transformer = FakeTransformer(scale = 2f, offset = PointF(10f, 20f))

        val corners = previewCorners(target, transformer)

        assertThat(corners).hasSize(4)
        assertThat(corners).containsExactly(
            Offset(10f, 20f),  // topLeft
            Offset(30f, 20f),  // topRight
            Offset(30f, 30f),  // bottomRight
            Offset(10f, 30f)   // bottomLeft
        ).inOrder()
    }

    @Test
    fun `previewCorners maps from image analysis into preview view`() {
        val target = targetWith(rectQuad(0f, 0f, 10f, 5f))
        val transformer = FakeTransformer()

        previewCorners(target, transformer)

        assertThat(transformer.lastFrom).isEqualTo(CoordinateSpace.IMAGE_ANALYSIS)
        assertThat(transformer.lastTo).isEqualTo(CoordinateSpace.PREVIEW_VIEW)
    }

    @Test
    fun `previewCorners maps a known rectangle to the expected rectangle`() {
        val target = targetWith(rectQuad(0f, 0f, 100f, 50f))
        val transformer = FakeTransformer(scale = 3f)

        val corners = previewCorners(target, transformer)

        assertThat(corners).containsExactly(
            Offset(0f, 0f),
            Offset(300f, 0f),
            Offset(300f, 150f),
            Offset(0f, 150f)
        ).inOrder()

        // Opposite sides of the mapped shape stay equal, i.e. it is still a rectangle.
        assertThat(corners[1].x - corners[0].x).isEqualTo(300f)
        assertThat(corners[2].x - corners[3].x).isEqualTo(300f)
        assertThat(corners[3].y - corners[0].y).isEqualTo(150f)
        assertThat(corners[2].y - corners[1].y).isEqualTo(150f)
    }
}
