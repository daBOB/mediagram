package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import model.together.Playback
import player.CastSenderViewModel
import ui.catalog.CastWayDialog
import ui.catalog.rememberOpenCastSettings

/** Play on TV as the player's card draws it: why it is greyed, or null, and what a press does. */
internal class PlayOnTvTool(
    val reason: String?,
    val press: () -> Unit,
)

/**
 * Play on TV in the player. Where the system's Cast screen opens, a press
 * asks which way ([CastWayDialog]) and the tool is never greyed, since this
 * device playing touches no channel; only the TV's choice carries
 * [CastSenderViewModel.castable]'s reason. The TV playing it offers what
 * plays here, as it plays ([playing]); this device playing it opens the Cast
 * screen and nothing else, and the picture moves once a TV screen connects
 * ([PresentingPlayer]). Without that screen a press offers straight away,
 * greyed with the reason, as Play on TV always did.
 */
@Composable
internal fun playOnTvTool(
    cast: CastSenderViewModel,
    playing: () -> Playback,
): PlayOnTvTool {
    val reason by cast.castable.collectAsStateWithLifecycle()
    val openCastScreen = rememberOpenCastSettings()
    var asking by rememberSaveable { mutableStateOf(false) }
    val offer = { cast.playOnTv(playing()) }
    if (openCastScreen == null) return PlayOnTvTool(reason, offer)
    if (asking) {
        CastWayDialog(
            tvReason = reason,
            onTv = {
                asking = false
                offer()
            },
            onThisDevice = {
                asking = false
                openCastScreen()
            },
            onDismiss = { asking = false },
        )
    }
    return PlayOnTvTool(reason = null) { asking = true }
}
