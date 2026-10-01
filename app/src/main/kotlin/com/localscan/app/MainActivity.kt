package com.localscan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.localscan.app.ui.camera.CameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalScanTheme {
                CameraScreen()
            }
        }
    }
}

@Composable
fun LocalScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
