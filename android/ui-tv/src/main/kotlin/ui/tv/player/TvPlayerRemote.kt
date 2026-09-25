// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.Util

/** Where the remote lands when a key brings the controls up. */
internal enum class TvControlsLanding { PlayPause, SeekBar }

/**
 * How far one press of a held Left/Right moves the film. A held key
 * repeats about twenty times a second after its first half-second, so ten
 * seconds a repeat already covers minutes quickly; the steps only grow once
 * the key has plainly been held on purpose, so that crossing a two-hour
 * film does not take the better part of a minute, while a tap or a short
 * hold still lands to the ten seconds the web and the phone skip.
 */
internal fun seekStepSeconds(
    seconds: Int,
    repeatCount: Int,
): Int =
    seconds *
        when {
            repeatCount < ACCELERATE_AFTER -> 1
            repeatCount < FASTEST_AFTER -> 3
            else -> 6
        }

/** About a second of holding: past a tap, into a deliberate hold. */
private const val ACCELERATE_AFTER = 20

/** About three seconds of holding. */
private const val FASTEST_AFTER = 60

/**
 * Applies [tvKeyAction]'s answers to the player and the controls. The table
 * decides; this only does — the one place a remote key becomes a media3
 * command, a show, or a focus landing.
 *
 * A key whose press is taken here has its repeats and its release taken
 * too, whatever the table would say of them on their own. Otherwise a
 * Centre press that brought the controls up would let the rest of it fall
 * on the play/pause button the controls just focused: a held press would
 * leave the button drawn pressed, and the release would press it a second
 * time.
 *
 * Back is not taken here at all: it goes on to the back dispatcher, where
 * the player screen answers it from the same table, so a Back that does
 * not arrive as a key — a gesture, a test — does the same thing as one
 * that does.
 */
internal class TvPlayerRemote(
    private val show: (TvControlsLanding) -> Unit,
) {
    private val taken = mutableSetOf<Key>()

    fun onKey(
        event: KeyEvent,
        player: Player?,
        controlsShowing: Boolean,
        onSeekBar: Boolean,
        canControl: Boolean,
    ): Boolean {
        if (event.type == KeyEventType.KeyUp) return taken.remove(event.key)
        if (event.type != KeyEventType.KeyDown || event.key == Key.Back) return false
        val repeat = event.nativeKeyEvent.repeatCount
        val took = apply(tvKeyAction(event.key, controlsShowing, onSeekBar, canControl), repeat, player)
        val heldOver = repeat > 0 && event.key in taken
        if (took) taken += event.key
        return took || heldOver
    }

    private fun apply(
        action: TvKeyAction,
        repeat: Int,
        player: Player?,
    ): Boolean =
        when (action) {
            // A held Centre or play key is one press, not a flicker of
            // play and pause twenty times a second.
            TvKeyAction.TogglePlay -> {
                if (repeat == 0) Util.handlePlayPauseButtonAction(player)
                true
            }
            TvKeyAction.Play -> {
                if (repeat == 0) Util.handlePlayButtonAction(player)
                true
            }
            TvKeyAction.Pause -> {
                if (repeat == 0) Util.handlePauseButtonAction(player)
                true
            }
            TvKeyAction.TogglePlayAndShowControls -> {
                if (repeat == 0) {
                    Util.handlePlayPauseButtonAction(player)
                    show(TvControlsLanding.PlayPause)
                }
                true
            }
            TvKeyAction.ShowControlsAndFocusSeekBar -> {
                if (repeat == 0) show(TvControlsLanding.SeekBar)
                true
            }
            is TvKeyAction.SeekBy -> {
                player?.let { seekBy(it, seekStepSeconds(action.seconds, repeat)) }
                true
            }
            // Lands on the seek bar, so the rest of a held press — and the
            // next tap — keeps moving the film rather than moving focus.
            is TvKeyAction.SeekByAndShowControls -> {
                player?.let { seekBy(it, seekStepSeconds(action.seconds, repeat)) }
                show(TvControlsLanding.SeekBar)
                true
            }
            TvKeyAction.HideControls, TvKeyAction.Leave, TvKeyAction.PassThrough, TvKeyAction.Ignore -> false
        }
}

/**
 * Moves the playhead by [seconds], kept inside the film. A length media3
 * does not know yet is `C.TIME_UNSET`; then only the start bounds the
 * seek, the same way the phone's own skip buttons behave before a length
 * is known.
 */
private fun seekBy(
    player: Player,
    seconds: Int,
) {
    if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
    val duration = player.duration
    val target = (player.currentPosition + seconds * 1_000L).coerceAtLeast(0L)
    player.seekTo(if (duration == C.TIME_UNSET) target else target.coerceAtMost(duration))
}
