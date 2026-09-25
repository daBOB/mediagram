package com.mediagram.android

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import data.WatchSync
import ui.LocalIsInPictureInPicture
import ui.MobileApp
import ui.PipEntryPoint
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
        // Seeded from the framework's own answer, not left at the default
        // `false`: a recreate this activity's `configChanges` does not
        // cover (dark mode, font scale, locale, density) can land here
        // already pinned, and the default would draw full chrome inside
        // that window until the next mode change told it otherwise.
        isInPip = isInPictureInPictureMode

        val onTelevision = isTelevision(this)
        setContent {
            if (onTelevision) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) { TvPlaceholder() }
                }
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

@Composable
private fun TvPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Mediagram — television surface")
    }
}
