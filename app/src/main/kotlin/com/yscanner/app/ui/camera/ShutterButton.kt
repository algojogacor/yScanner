package com.yscanner.app.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ShutterButton(
    enabled: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        label = "ShutterPressScale"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isCapturing,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(76.dp)) {
            // Outer ring
            drawCircle(
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 4.dp.toPx())
            )
            // Inner shutter disk
            if (!isCapturing) {
                drawCircle(
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    radius = (size.minDimension / 2f - 6.dp.toPx()) * scale
                )
            }
        }

        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
    }
}
