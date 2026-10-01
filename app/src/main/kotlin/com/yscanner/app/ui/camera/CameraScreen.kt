package com.yscanner.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Check permission on initial launch
    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.onPermissionResult(currentPermission)
        if (!currentPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Surface errors via Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.hasPermission) {
            // 1. Live Viewfinder
            CameraPreview(
                onPreviewViewReady = { previewView ->
                    viewModel.initializeCamera(context, lifecycleOwner, previewView)
                },
                onTap = { point ->
                    viewModel.onPreviewTapped(point)
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Tap-to-Focus Reticle Overlay
            FocusReticle(
                targetPoint = uiState.lastFocusPoint,
                triggerKey = uiState.focusTriggerKey
            )

            // 3. Top Control Bar (Flash Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlashButton(
                    currentMode = uiState.flashMode,
                    enabled = uiState.isReady && uiState.hasFlashUnit,
                    onModeChanged = { nextMode ->
                        viewModel.onFlashModeChanged(nextMode)
                    }
                )
            }

            // 4. Bottom Control Bar (Shutter Button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                ShutterButton(
                    enabled = uiState.isReady,
                    isCapturing = uiState.isCapturing,
                    onClick = {
                        viewModel.capturePhoto(context.cacheDir)
                    }
                )
            }
        } else {
            // Permission Denied / Rationale View
            PermissionRationale(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
        )
    }
}
