package com.localscan.app.ui.camera

import com.localscan.camera.capture.CaptureResult
import com.localscan.domain.model.FlashMode
import com.localscan.domain.model.PointF

data class CameraUiState(
    val hasPermission: Boolean = false,
    val isReady: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val hasFlashUnit: Boolean = true,
    val isCapturing: Boolean = false,
    val lastFocusPoint: PointF? = null,
    val focusTriggerKey: Long = 0L,
    val lastCaptureResult: CaptureResult? = null,
    val errorMessage: String? = null
)
