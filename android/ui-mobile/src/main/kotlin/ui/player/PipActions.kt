package ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import com.mediagram.android.ui.mobile.R

/** The narrowest and widest window `PictureInPictureParams` accepts — a video outside this (a phone-shot vertical recording, an ultra-wide scope film) is rejected outright rather than merely cropped. */
private const val MIN_ASPECT = 1.0 / 2.39
private const val MAX_ASPECT = 2.39 / 1.0

/**
 * The aspect ratio (width/height) the picture-in-picture window opens at,
 * clamped to what `PictureInPictureParams` will actually accept — see
 * [MIN_ASPECT]/[MAX_ASPECT]. Falls back to 16:9 with no measured video size
 * yet (the button pressed, or a home gesture crossed, before the first
 * frame has reported one).
 *
 * A plain [Double] rather than [Rational] itself — [pipAspectRational]
 * does that conversion — so this, the actual clamping math, is a pure
 * function a JVM test can call directly; `Rational`'s own methods are
 * Android SDK stubs outside Robolectric, same as `Uri`'s in
 * `DefaultPlayerHandleTest`.
 */
internal fun clampedPipAspect(videoWidth: Int, videoHeight: Int): Double {
    if (videoWidth <= 0 || videoHeight <= 0) return 16.0 / 9.0
    return (videoWidth.toDouble() / videoHeight.toDouble()).coerceIn(MIN_ASPECT, MAX_ASPECT)
}

/**
 * [Rational], exactly — never [clampedPipAspect]'s own [Double] scaled
 * back up through `(ratio * 1000).toInt()`: that round-trip truncates
 * 1/2.39 = 0.41841… down to 0.418, which the framework rejects as "too
 * extreme" (it is narrower than the true minimum, not equal to it). The
 * unclamped case passes [videoWidth]/[videoHeight] straight through, exact;
 * the two clamped cases use the platform's own bounds as exact fractions
 * — `1000/2390` reduces to `100/239`, exactly `1/2.39` — instead of a
 * lossy decimal approximation of them.
 */
internal fun pipAspectRational(videoWidth: Int, videoHeight: Int): Rational {
    if (videoWidth <= 0 || videoHeight <= 0) return Rational(16, 9)
    val ratio = videoWidth.toDouble() / videoHeight.toDouble()
    return when {
        ratio < MIN_ASPECT -> Rational(1000, 2390)
        ratio > MAX_ASPECT -> Rational(239, 100)
        else -> Rational(videoWidth, videoHeight)
    }
}

/**
 * What entering picture-in-picture actually asks for — the window's own
 * shape plus the three actions Android draws over it, since there is no
 * touch surface of this app's own inside that window for a viewer to press
 * instead. [Player.getVideoSize] is read once, at the moment this is built
 * (on the same play-state change that would also change it), rather than
 * tracked continuously: by the time a title is actually playing, media3
 * has already measured its format.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun buildPipParams(context: Context, player: Player?, isPlaying: Boolean): PictureInPictureParams {
    val videoSize = player?.videoSize
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(pipAspectRational(videoSize?.width ?: 0, videoSize?.height ?: 0))
        .setActions(pipRemoteActions(context, isPlaying))
    // Below API 31 there is no such flag; MainActivity.onUserLeaveHint is
    // the only way to enter on a home gesture there, through PipEntryPoint.
    if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(pipAutoEnterEligible(isPlaying, player?.playWhenReady == true))
    return builder.build()
}

/**
 * Whether a home gesture right now should shrink into picture-in-picture
 * rather than merely background the app — [isPlaying] alone missed a
 * title still buffering ([playWhenReady] true, `Playing` not yet reached):
 * a home press in that window backgrounded the app with no PiP window and
 * no way back to it once it started playing, the same leak closing PiP
 * itself used to leave open. Takes the plain booleans rather than a
 * `Player?`, so this stays a pure function a JVM test can call directly —
 * nothing here about a title finishing or erroring changes on its own;
 * both already flip `playWhenReady` off first.
 */
internal fun pipAutoEnterEligible(isPlaying: Boolean, playWhenReady: Boolean): Boolean = isPlaying || playWhenReady

internal const val PIP_ACTION_MEDIA_CONTROL = "com.mediagram.android.PIP_MEDIA_CONTROL"
internal const val PIP_EXTRA_CONTROL_TYPE = "control_type"
private const val CONTROL_REWIND = 1
private const val CONTROL_PLAY_PAUSE = 2
private const val CONTROL_FORWARD = 3

@RequiresApi(Build.VERSION_CODES.O)
private fun pipRemoteActions(context: Context, isPlaying: Boolean): List<RemoteAction> = listOf(
    pipRemoteAction(context, CONTROL_REWIND, R.drawable.ui_mobile_ic_pip_rewind, "Skip back"),
    if (isPlaying) {
        pipRemoteAction(context, CONTROL_PLAY_PAUSE, R.drawable.ui_mobile_ic_pip_pause, "Pause")
    } else {
        pipRemoteAction(context, CONTROL_PLAY_PAUSE, R.drawable.ui_mobile_ic_pip_play, "Play")
    },
    pipRemoteAction(context, CONTROL_FORWARD, R.drawable.ui_mobile_ic_pip_forward, "Skip forward"),
)

@RequiresApi(Build.VERSION_CODES.O)
private fun pipRemoteAction(context: Context, controlType: Int, iconRes: Int, title: String): RemoteAction {
    val intent = Intent(PIP_ACTION_MEDIA_CONTROL)
        .setPackage(context.packageName)
        .putExtra(PIP_EXTRA_CONTROL_TYPE, controlType)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        controlType,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pendingIntent)
}

/**
 * Where [pipRemoteAction]'s three buttons actually land — registered only
 * while a player screen is on screen (see [PipController]), on the same
 * [Player] the transport bar's own buttons already act on, through the
 * same buffering-aware play/pause toggle [PlayerGestureLayer] uses.
 */
internal class PipActionReceiver(private val player: Player?) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = player ?: return
        when (intent.getIntExtra(PIP_EXTRA_CONTROL_TYPE, -1)) {
            CONTROL_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(player)
            CONTROL_REWIND -> player.seekBack()
            CONTROL_FORWARD -> player.seekForward()
        }
    }
}
