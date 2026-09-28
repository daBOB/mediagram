package com.mediagram.android

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import data.WatchSync
import ui.player.LocalIsInPictureInPicture
import ui.MobileApp
import ui.player.PipEntryPoint
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
    }

    override fun onStop() {
        watchSync.onBackground()
        super.onStop()
    }

    /**
     * Below API 31, `PictureInPictureParams.Builder.setAutoEnterEnabled`
     * does not exist and this is the only moment the system offers to
     * enter picture-in-picture on the home gesture. [PipEntryPoint] is
     * whatever the mounted player screen last asked for — null with no
     * player playing, or none open at all, which leaves the app simply
     * backgrounding, same as ever.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in 26..30) PipEntryPoint.onUserLeaveHint?.invoke()
    }

    /**
     * The system reports leaving picture-in-picture two different ways
     * through this one callback: expanding the window back to full screen
     * (the activity is already `STARTED`/`RESUMED` again by the time this
     * runs, as part of the same transition), and dismissing it outright —
     * the ✕, or swiping it away — which stops the activity *first* and
     * moves its task to the back, landing this at `CREATED` instead.
     * [PipEntryPoint.onDismissed] is only ever the latter: a viewer who
     * expanded back is looking at their film full-screen, not asking for
     * it to pause.
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        if (isPipDismissal(isInPictureInPictureMode, lifecycle.currentState)) {
            PipEntryPoint.onDismissed?.invoke()
        }
    }
}

/**
 * Told apart from expanding picture-in-picture back to full screen (the
 * activity is already `STARTED`/`RESUMED` again by the time
 * `onPictureInPictureModeChanged` runs, as part of that same transition)
 * by whether the activity's own lifecycle has already dropped to
 * `CREATED` — dismissal (the ✕, or swiping the window away) stops the
 * activity *first* and moves its task to the back. Split out as a pure
 * function so a test can call it without a real Activity/lifecycle.
 */
internal fun isPipDismissal(isInPictureInPictureMode: Boolean, lifecycleState: Lifecycle.State): Boolean =
    !isInPictureInPictureMode && lifecycleState == Lifecycle.State.CREATED
