package com.localscan.camera

import com.localscan.domain.model.FlashMode

interface CameraController {
    fun setFlashMode(mode: FlashMode)
    fun setTorchEnabled(enabled: Boolean)
}
