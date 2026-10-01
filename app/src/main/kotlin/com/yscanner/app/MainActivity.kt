package com.yscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.yscanner.app.ui.camera.CameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YScannerTheme {
                CameraScreen()
            }
        }
    }
}

@Composable
fun YScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
