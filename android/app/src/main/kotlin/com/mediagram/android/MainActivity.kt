package com.mediagram.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import data.WatchSync
import ui.MobileApp
import ui.tv.TvApp
import javax.inject.Inject

/**
 * Nothing is gated here any more. A freshly installed APK carries no
 * Telegram credentials of any kind, and asks for them on screen: the app
 * decides what to show from what the device has actually stored, which is
 * the one thing a build on someone else's machine cannot have decided for
 * it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Field-injected rather than read from a ViewModel: the watch-state
    // cadence is a property of the process, not of this screen, and
    // onStart/onStop are Activity lifecycle callbacks a Composable has no
    // equivalent for that also fires on a phone simply being locked.
    @Inject
    lateinit var watchSync: WatchSync

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the first frame, and before Hilt: the bars are told they
        // are sitting over a dark app, so their icons come up light. The
        // insets are already handled — the scaffold and the setup screens
        // both consume them — and this only settles appearance.
        //
        // Both styles are `dark` rather than `auto`, for the same reason
        // the theme is: this app is dark whatever the system is set to, so
        // a light-mode device must not be handed dark icons on ink.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val onTelevision = isTelevision(this)
        setContent {
            if (onTelevision) {
                TvApp()
            } else {
                MobileApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        watchSync.onForeground()
    }

    override fun onStop() {
        watchSync.onBackground()
        super.onStop()
    }
}
