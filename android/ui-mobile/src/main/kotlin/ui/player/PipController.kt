package ui.player

import android.app.PictureInPictureParams
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

/**
 * What `MainActivity` needs to hand `ui-mobile` for two moments it alone
 * sees — `ui-mobile` cannot import `MainActivity` back (the module graph
 * runs the other way), so these two settable slots are what cross instead.
 * There is only ever one player screen mounted at a time, so one held
 * reference each is enough; [PipController] sets both while composed,
 * clears both on disposal. Public, deliberately: `internal` would shut
 * `:app`, a different Gradle module, out of reading them.
 */
object PipEntryPoint {
    /** API 26..30 only, where `setAutoEnterEnabled` does not exist and `onUserLeaveHint` is the only offer to enter picture-in-picture on the home gesture. */
    @Volatile
    var onUserLeaveHint: (() -> Unit)? = null

    /**
     * The system dismissed the picture-in-picture window (the ✕, or the
     * swipe-away gesture) rather than the viewer expanding it back —
     * `MainActivity` tells the two apart by whether its own lifecycle has
     * already dropped to `CREATED` by the time `onPictureInPictureModeChanged(false)`
     * lands (dismissal stops the activity first; expanding back does not).
     */
    @Volatile
    var onDismissed: (() -> Unit)? = null
}

/** What the top bar's own picture-in-picture button needs — `null`/no button when this device or API level has no support for it, or with no activity to enter it on. */
internal class PipButtonState(val supported: Boolean, val enterPip: () -> Unit)

/**
 * Keeps the activity's `PictureInPictureParams` in step with [player] and
 * [isPlaying], wires the home-gesture auto-enter path for API 26..30 and
 * the dismissal callback through [PipEntryPoint], and registers
 * [PipActionReceiver] for the window's own play/pause/seek buttons — all
 * for as long as a player screen is composed. [onDismissed] is asked to
 * pause and save (never a full stop) the moment [PipEntryPoint.onDismissed]
 * fires; see `PlayerViewModel.pauseForPipDismissal`.
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
        PipEntryPoint.onUserLeaveHint = {
            if (pipAutoEnterEligible(isPlaying, player?.playWhenReady == true)) currentActivity.enterPictureInPictureMode(params)
        }
        PipEntryPoint.onDismissed = onDismissed
        onDispose {
            PipEntryPoint.onUserLeaveHint = null
            PipEntryPoint.onDismissed = null
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
