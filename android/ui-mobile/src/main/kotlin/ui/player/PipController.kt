package ui.player

import android.app.PictureInPictureParams
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player

/**
 * Whether the activity is currently shrunk into picture-in-picture —
 * published by `MainActivity.onPictureInPictureModeChanged`, read by
 * `PlayerScreen` to hide its own chrome (there is no touch surface of this
 * app's own inside that window; see [PipActionReceiver]) the moment the
 * system resizes it, not a frame later. Defaults to `false` for any
 * composable under a host that never provides it (previews, tests).
 */
val LocalIsInPictureInPicture: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** What the top bar's own picture-in-picture button needs — `null`/no button when this device or API level has no support for it, or with no activity to enter it on. */
internal class PipButtonState(val supported: Boolean, val enterPip: () -> Unit)

/**
 * Keeps the activity's `PictureInPictureParams` in step with [player] and
 * [isPlaying], listens on the activity for the home gesture (API 26..30,
 * where there is no auto-enter) and for the picture-in-picture window
 * being dismissed, and registers [PipActionReceiver] for the window's own
 * play/pause/seek buttons — all for as long as a player screen is
 * composed. [onDismissed] is asked to pause and save (never a full stop)
 * the moment the window is dismissed; see `PlayerViewModel.pauseForPipDismissal`.
 *
 * [supported] gates every picture-in-picture API this touches, including
 * [enterPip] below: `Build.VERSION.SDK_INT >= 26` alone is not enough —
 * devices that declare the feature `required="false"` (Android Go, some
 * OEM builds) throw `IllegalStateException` from `setPictureInPictureParams`
 * and `enterPictureInPictureMode` alike, and the params effect below runs
 * on every play-state change, not just on a button press.
 */
@Composable
internal fun PipController(player: Player?, isPlaying: Boolean, onDismissed: () -> Unit): PipButtonState {
    val activity = LocalContext.current.findActivity()
    val supported = Build.VERSION.SDK_INT >= 26 &&
        activity != null &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    DisposableEffect(activity, player, isPlaying) {
        // The `Build.VERSION.SDK_INT` check is redundant with `supported`
        // (above already requires it) but written out again regardless:
        // lint's own version-check recognizer does not see through a
        // boolean local computed in a different statement, and every
        // picture-in-picture call below needs it satisfied right here.
        if (!supported || Build.VERSION.SDK_INT < 26) return@DisposableEffect onDispose {}
        val currentActivity = activity // Non-null: `supported` (above) already required it.
        val params = buildPipParams(currentActivity, player, isPlaying)
        currentActivity.setPictureInPictureParams(params)
        val host = currentActivity as? ComponentActivity
        // Below API 31 there is no auto-enter, and the user-leave hint is the
        // only moment the system offers to enter on a home gesture.
        val leaveHint =
            Runnable {
                if (pipAutoEnterEligible(isPlaying, player?.playWhenReady == true)) currentActivity.enterPictureInPictureMode(params)
            }.takeIf { Build.VERSION.SDK_INT <= 30 }
        val modeChanged =
            Consumer<PictureInPictureModeChangedInfo> { info ->
                if (host != null && isPipDismissal(info.isInPictureInPictureMode, host.lifecycle.currentState)) onDismissed()
            }
        leaveHint?.let { host?.addOnUserLeaveHintListener(it) }
        host?.addOnPictureInPictureModeChangedListener(modeChanged)
        onDispose {
            leaveHint?.let { host?.removeOnUserLeaveHintListener(it) }
            host?.removeOnPictureInPictureModeChangedListener(modeChanged)
            // Otherwise this stays armed on the activity itself after the
            // player screen is gone: a viewer who backs out mid-film and
            // then swipes home would shrink the catalog into a picture
            // window with no receiver left to answer its own actions.
            if (Build.VERSION.SDK_INT >= 31) {
                currentActivity.setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).setActions(emptyList()).build(),
                )
            }
        }
    }

    DisposableEffect(activity, player) {
        if (!supported || Build.VERSION.SDK_INT < 26) return@DisposableEffect onDispose {}
        val currentActivity = activity // Non-null: see the effect above.
        val receiver = PipActionReceiver(player)
        ContextCompat.registerReceiver(
            currentActivity,
            receiver,
            IntentFilter(PIP_ACTION_MEDIA_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { currentActivity.unregisterReceiver(receiver) }
    }

    return PipButtonState(supported = supported) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity?.let { it.enterPictureInPictureMode(buildPipParams(it, player, isPlaying)) }
        }
    }
}

/**
 * Told apart from expanding picture-in-picture back to full screen (the
 * activity is already `STARTED`/`RESUMED` again by the time the mode change
 * lands, as part of that same transition) by whether the activity's own
 * lifecycle has already dropped to `CREATED` — dismissal (the ✕, or swiping
 * the window away) stops the activity *first* and moves its task to the
 * back. A pure function so a test can call it without a real Activity.
 */
internal fun isPipDismissal(isInPictureInPictureMode: Boolean, lifecycleState: Lifecycle.State): Boolean =
    !isInPictureInPictureMode && lifecycleState == Lifecycle.State.CREATED
