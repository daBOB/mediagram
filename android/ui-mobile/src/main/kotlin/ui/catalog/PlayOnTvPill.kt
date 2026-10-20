package ui.catalog

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import model.together.CastWords
import model.together.Playback
import player.CastSenderViewModel
import player.PresentationViewModel
import ui.chrome.CatalogueOutlinedButton

/**
 * Play on TV on a title page: why the TV box can't be offered to ([reason],
 * `null` when it can), whether a TV screen is already [displayConnected] to
 * this device, and [cast], which offers a title from a second.
 */
class PlayOnTv(
    val reason: String?,
    val displayConnected: Boolean = false,
    val cast: (setId: String, pos: Double) -> Unit,
)

/** Play on TV as [CastSenderViewModel] has it; what the TV answers is [CastAskHost]'s. */
@Composable
internal fun rememberPlayOnTv(
    sender: CastSenderViewModel = hiltViewModel(),
    displays: PresentationViewModel = hiltViewModel(),
): PlayOnTv {
    val reason by sender.castable.collectAsStateWithLifecycle()
    val connected by displays.connected.collectAsStateWithLifecycle()
    return remember(sender, reason, connected) {
        PlayOnTv(reason, connected) { setId, pos -> sender.playOnTv(Playback(setId, playing = true, pos = pos, rate = 1.0)) }
    }
}

/**
 * The pill after Play or Resume, the web's `castPill`: [setId] from [pos],
 * the point that pill would start at. Outlined, as My List is; greyed with
 * the reason, which a screen reader hears after its name.
 *
 * Where the system's Cast screen opens, a press asks which way, as the
 * player's tool does ([CastWayDialog]), and the pill is never greyed: the TV
 * box takes the offer from this point, or this device plays it, which is
 * [onPlayHere] (what the page's own Play does) and then the Cast screen,
 * unless a TV screen is connected already and the player presents by itself.
 */
@Composable
internal fun PlayOnTvPill(
    playOnTv: PlayOnTv,
    setId: String,
    pos: Double,
    onPlayHere: () -> Unit,
) {
    val openCastScreen = rememberOpenCastSettings()
    var asking by rememberSaveable { mutableStateOf(false) }
    val reason = if (openCastScreen == null) playOnTv.reason else null
    val words = listOfNotNull(CastWords.PLAY_ON_TV, reason).joinToString(", ")
    if (asking && openCastScreen != null) {
        CastWayDialog(
            tvReason = playOnTv.reason,
            onTv = {
                asking = false
                playOnTv.cast(setId, pos)
            },
            onThisDevice = {
                asking = false
                onPlayHere()
                if (!playOnTv.displayConnected) openCastScreen()
            },
            onDismiss = { asking = false },
        )
    }
    CatalogueOutlinedButton(
        onClick = { if (openCastScreen == null) playOnTv.cast(setId, pos) else asking = true },
        enabled = reason == null,
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).semantics { contentDescription = words },
    ) {
        Text(CastWords.PLAY_ON_TV)
    }
}
