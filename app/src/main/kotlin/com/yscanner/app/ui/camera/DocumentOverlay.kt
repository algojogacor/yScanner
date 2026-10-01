package com.yscanner.app.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yscanner.detection.select.TrackingState
import com.yscanner.detection.tracking.SmoothedTarget
import com.yscanner.geometry.CoordinateSpace
import com.yscanner.geometry.CoordinateTransformer

/**
 * The overlay's visual style for a given tracking state.
 *
 * Pure data with no Compose types, so [overlayStyleFor] can be asserted from a plain JVM unit test.
 *
 * @param argb packed `0xAARRGGBB` colour. Kept as a [Long] rather than a `Color` so this file's
 *   style logic stays independent of Compose.
 * @param strokeWidthDp outline width in density-independent pixels.
 * @param filled when true the renderer also paints a translucent interior in the same colour. All
 *   four states currently set this to false; the flag exists so a style can opt into a fill without
 *   changing [DocumentOverlay].
 */
data class OverlayStyle(
    val argb: Long,
    val strokeWidthDp: Float,
    val filled: Boolean
)

/**
 * Maps a tracking state to its visual style.
 *
 * Pure function: same input, same output, no Compose and no side effects. The palette is chosen so
 * the four states are distinguishable from one another by colour alone:
 *
 * * [TrackingState.ACQUIRING] — amber, thin. A target is visible but not yet confirmed.
 * * [TrackingState.TRACKING] — white, medium. Identity is continuous.
 * * [TrackingState.STABLE] — green, thickest. Held long enough to be captured.
 * * [TrackingState.LOST] — grey at reduced alpha, thin. The outline is fading out; the
 *   non-opaque alpha is what tells a caller that this state is no longer a live target.
 */
fun overlayStyleFor(state: TrackingState): OverlayStyle = when (state) {
    TrackingState.ACQUIRING -> OverlayStyle(argb = 0xFFFFC107, strokeWidthDp = 2f, filled = false)
    TrackingState.TRACKING -> OverlayStyle(argb = 0xFFFFFFFF, strokeWidthDp = 3f, filled = false)
    TrackingState.STABLE -> OverlayStyle(argb = 0xFF4CAF50, strokeWidthDp = 4f, filled = false)
    TrackingState.LOST -> OverlayStyle(argb = 0x99BDBDBD, strokeWidthDp = 2f, filled = false)
}

/**
 * Projects a target's quad into preview-view pixels, as four corners clockwise from the top-left.
 *
 * The mapping goes `IMAGE_ANALYSIS -> PREVIEW_VIEW` through [transformer]; the caller is responsible
 * for supplying a transformer that already knows the current preview size and scale type. The
 * returned order matches [com.yscanner.domain.model.Quad]'s corner order, so the result can be
 * connected in sequence to form the outline.
 *
 * This is a pure function. It does not guard against a throwing or degenerate [transformer] — that
 * guard lives in [DocumentOverlay], which is the only caller that can afford to draw nothing.
 */
fun previewCorners(target: SmoothedTarget, transformer: CoordinateTransformer): List<Offset> {
    val mapped = transformer.mapQuad(
        target.quad,
        CoordinateSpace.IMAGE_ANALYSIS,
        CoordinateSpace.PREVIEW_VIEW
    )
    return listOf(
        Offset(mapped.topLeft.x, mapped.topLeft.y),
        Offset(mapped.topRight.x, mapped.topRight.y),
        Offset(mapped.bottomRight.x, mapped.bottomRight.y),
        Offset(mapped.bottomLeft.x, mapped.bottomLeft.y)
    )
}

/**
 * Draws the tracked document outline over the camera preview.
 *
 * Stateless and side-effect free: it holds no camera, no tracker and no animation, and only reads
 * the [target] handed to it each recomposition. Intended to be stacked directly on top of the
 * preview inside a `Box`, hence the `fillMaxSize` modifier.
 *
 * Draws nothing when [target] is `null`. It also draws nothing when the coordinate mapping throws or
 * yields a non-finite coordinate: a degenerate transformer should leave the preview without an
 * outline, never crash it. That is why the mapping is wrapped in [runCatching] rather than trusted.
 *
 * This composable has never been executed on a device or emulator — none is available in this
 * environment — so its on-screen output, including how the outline lines up with the preview
 * surface, is unverified.
 *
 * @param target the smoothed target to outline, or `null` for no outline.
 * @param transformer maps the target's `IMAGE_ANALYSIS` quad into `PREVIEW_VIEW` pixels.
 * @param modifier layout modifier; the canvas fills the incoming constraints.
 */
@Composable
fun DocumentOverlay(
    target: SmoothedTarget?,
    transformer: CoordinateTransformer,
    modifier: Modifier = Modifier
) {
    if (target == null) return

    val corners = runCatching { previewCorners(target, transformer) }
        .getOrNull()
        ?.takeIf { points -> points.all { it.x.isFinite() && it.y.isFinite() } }
        ?: return

    val style = overlayStyleFor(target.trackingState)
    val color = Color(style.argb)

    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            lineTo(corners[1].x, corners[1].y)
            lineTo(corners[2].x, corners[2].y)
            lineTo(corners[3].x, corners[3].y)
            close()
        }
        if (style.filled) {
            drawPath(path = path, color = color.copy(alpha = color.alpha * 0.2f))
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = style.strokeWidthDp.dp.toPx())
        )
    }
}
