package com.mediagram.android

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import data.WatchSync
import data.isTelevision
import ui.player.LocalIsInPictureInPicture
import ui.MobileApp
import ui.tv.TvApp
import update.AppUpdater
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

    @Inject
    lateinit var appUpdater: AppUpdater

    // Mirrors `onPictureInPictureModeChanged` for `LocalIsInPictureInPicture`
    // — `PlayerScreen` reads this to hide its own chrome the moment the
    // system resizes the window, not a frame later.
    private var isInPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the first frame, and before Hilt: the bars take their icon
        // colour from this configuration's night mode, which on API 31+ is
        // the app's own — Settings › Appearance hands its choice to the
        // system — and below that the device's. `MediagramTheme`'s own
        // `SideEffect` corrects the phone's bars once Compose draws, and on
        // every change after. The television is dark whatever it says.
        // The insets are already handled — the scaffold and the setup
        // screens both consume them — and this only settles appearance.
        val onTelevision = isTelevision(this)
        val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (onTelevision || nightMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        super.onCreate(savedInstanceState)
        // Seeded from the framework's own answer, not left at the default
        // `false`: a recreate this activity's `configChanges` does not
        // cover (dark mode, font scale, locale, density) can land here
        // already pinned, and the default would draw full chrome inside
        // that window until the next mode change told it otherwise.
        isInPip = isInPictureInPictureMode

        setContent {
            if (onTelevision) {
                TvApp()
            } else {
                CompositionLocalProvider(LocalIsInPictureInPicture provides isInPip) {
                    MobileApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        watchSync.onForeground()
        appUpdater.onForeground()
        // A confirm screen Android asked for while the app was in the background (installs from this app not allowed yet, or Android 10–11).
        appUpdater.takeConfirmIntent()?.let { runCatching { startActivity(it) } }
    }

    override fun onStop() {
        watchSync.onBackground()
        appUpdater.onBackground()
        super.onStop()
    }

    /** Mirrors the mode for `LocalIsInPictureInPicture`; the player screen listens for a dismissal itself (`ui.player.PipController`). */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }
}
