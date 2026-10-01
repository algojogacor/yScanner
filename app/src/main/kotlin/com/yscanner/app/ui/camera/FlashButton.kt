package com.yscanner.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yscanner.domain.model.FlashMode

@Composable
fun FlashButton(
    currentMode: FlashMode,
    enabled: Boolean,
    onModeChanged: (FlashMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextMode = when (currentMode) {
        FlashMode.OFF -> FlashMode.ON
        FlashMode.ON -> FlashMode.TORCH
        FlashMode.TORCH -> FlashMode.OFF
    }

    val (label, accentColor) = when (currentMode) {
        FlashMode.OFF -> "FLASH OFF" to Color.White.copy(alpha = 0.7f)
        FlashMode.ON -> "FLASH ON" to Color(0xFFFFD700)
        FlashMode.TORCH -> "TORCH ON" to Color(0xFFFF9800)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onModeChanged(nextMode) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = when (currentMode) {
                FlashMode.OFF -> "⚡"
                FlashMode.ON -> "⚡"
                FlashMode.TORCH -> "🔦"
            },
            fontSize = 14.sp
        )
        Text(
            text = label,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
