package com.streamapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.streamapp.core.designsystem.theme.BackgroundDark
import com.streamapp.core.designsystem.theme.StreamAppTheme
import com.streamapp.features.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StreamAppTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark),
                    color = BackgroundDark
                ) {
                    MainScreen()
                }
            }
        }
    }
}
