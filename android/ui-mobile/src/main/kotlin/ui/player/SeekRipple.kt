package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import designsystem.Spacing
import kotlinx.coroutines.delay

/** Which third of the picture a double tap landed in — the outer thirds seek, the middle toggles play/pause, ported from `PlayerGestureLayer`'s own split of the screen rather than measured against the video box; see there for why. */
internal enum class SeekZone { BACK, FORWARD, PLAY_PAUSE }

/** A double-tap seek, still showing — the side it seeked, the total seconds accumulated across however many double-taps have landed there since the last pause on the other side, and when the most recent one landed. */
internal data class SeekFlash(val zone: SeekZone, val totalSeconds: Long, val atMs: Long)

/** How long a double tap on the same side keeps adding to the flash already showing rather than starting a new one — long enough that two quick double-taps read as one seek of twenty, not two of ten shown in sequence. */
internal const val SEEK_FLASH_ACCUMULATE_MS = 900L

/** How long a flash stays up after the last double-tap that fed it. */
private const val SEEK_FLASH_LINGER_MS = 700L

/**
 * What the next flash is, given the one still showing (or none). Pure, so
 * the accumulation rule — same side, soon enough — is provable without a
 * gesture to drive it.
 */
internal fun accumulateFlash(previous: SeekFlash?, zone: SeekZone, incrementSeconds: Long, nowMs: Long): SeekFlash {
    val stillAccumulating = previous != null && previous.zone == zone && nowMs - previous.atMs <= SEEK_FLASH_ACCUMULATE_MS
    val total = if (stillAccumulating) previous!!.totalSeconds + incrementSeconds else incrementSeconds
    return SeekFlash(zone, total, nowMs)
}

/** "−10 s", "+20 s" — signed, so a viewer can tell a seek back from a seek forward without reading a direction off an icon. */
internal fun seekFlashLabel(flash: SeekFlash): String {
    val sign = if (flash.zone == SeekZone.BACK) "−" else "+"
    return "$sign${flash.totalSeconds} s"
}

/**
 * The brief "-10 s" / "+10 s" a double-tap seek leaves over the picture.
 * [flash] is never cleared by [PlayerGestureLayer] — a new double-tap simply
 * replaces it with a fresh one, even on the far side — so hiding after
 * [SEEK_FLASH_LINGER_MS] is this composable's own job, keyed on
 * [SeekFlash.atMs] so a fresh double-tap always restarts the timer rather
 * than being cut short by one already running out.
 */
@Composable
internal fun SeekRipple(flash: SeekFlash?, modifier: Modifier = Modifier) {
    if (flash == null || flash.zone == SeekZone.PLAY_PAUSE) return
    var visible by remember(flash.atMs) { mutableStateOf(true) }
    LaunchedEffect(flash.atMs) {
        delay(SEEK_FLASH_LINGER_MS)
        visible = false
    }
    if (!visible) return
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(Spacing.large)) {
        Text(text = seekFlashLabel(flash), color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}
