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
import settings.PackageSettings
import ui.MobileApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var packageSettings: PackageSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onTelevision = isTelevision(this)
        setContent {
            if (onTelevision) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) { TvPlaceholder() }
                }
            } else {
                MobileApp(hasApiCredentials = hasApiCredentials(), packageSettings = packageSettings)
            }
        }
    }
}

/**
 * The Telegram *application* identity, populated at build time from
 * `local.properties`. Never logged: a blank value means the human hasn't
 * supplied it yet, not that anything is broken.
 */
private fun hasApiCredentials(): Boolean =
    BuildConfig.MEDIAGRAM_API_ID.toIntOrNull() != null && BuildConfig.MEDIAGRAM_API_HASH.isNotBlank()

@Composable
private fun TvPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Mediagram — television surface")
    }
}
