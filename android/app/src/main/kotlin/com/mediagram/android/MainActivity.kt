package com.mediagram.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ui.MobileApp

/**
 * Nothing is gated here any more. A freshly installed APK carries no
 * Telegram credentials of any kind, and asks for them on screen: the app
 * decides what to show from what the device has actually stored, which is
 * the one thing a build on someone else's machine cannot have decided for
 * it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onTelevision = isTelevision(this)
        setContent {
            if (onTelevision) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) { TvPlaceholder() }
                }
            } else {
                MobileApp()
            }
        }
    }
}

@Composable
private fun TvPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Mediagram — television surface")
    }
}
