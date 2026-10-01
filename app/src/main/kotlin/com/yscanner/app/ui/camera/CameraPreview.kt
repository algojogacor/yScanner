package com.yscanner.app.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.yscanner.domain.model.PointF

/**
 * Jetpack Compose wrapper for CameraX [PreviewView].
 *
 * Configured with [PreviewView.ScaleType.FILL_CENTER] for full-screen immersive document scanning.
 * Intercepts tap gestures and passes view-relative coordinates to [onTap].
 *
 * @param onPreviewViewReady Callback invoked when [PreviewView] is instantiated and attached to hierarchy.
 * @param onTap Callback invoked with tap coordinates in [PreviewView] pixel space.
 * @param modifier Composable layout modifier.
 */
@Composable
fun CameraPreview(
    onPreviewViewReady: (PreviewView) -> Unit,
    onTap: (PointF) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTap(PointF(offset.x, offset.y))
                }
            },
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                onPreviewViewReady(this)
            }
        }
    )
}
