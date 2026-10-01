package com.yscanner.app.ui.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yscanner.domain.model.PointF
import kotlin.math.roundToInt

@Composable
fun FocusReticle(
    targetPoint: PointF?,
    triggerKey: Long,
    modifier: Modifier = Modifier
) {
    if (targetPoint == null) return

    val alpha = remember(triggerKey) { Animatable(1f) }
    val scale = remember(triggerKey) { Animatable(1.3f) }

    LaunchedEffect(triggerKey) {
        scale.animateTo(1f, animationSpec = tween(150))
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 800, delayMillis = 1000))
    }

    if (alpha.value > 0f) {
        val sizeDp = 64.dp
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (targetPoint.x - (sizeDp.toPx() * scale.value / 2f)).roundToInt(),
                            y = (targetPoint.y - (sizeDp.toPx() * scale.value / 2f)).roundToInt()
                        )
                    }
                    .size(sizeDp * scale.value)
            ) {
                drawRoundRect(
                    color = Color.White.copy(alpha = alpha.value * 0.9f),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
