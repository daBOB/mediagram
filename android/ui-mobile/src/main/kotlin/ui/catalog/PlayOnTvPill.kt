package ui.catalog

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import model.together.CastWords
import model.together.Playback
import player.CastSenderViewModel
import ui.chrome.CatalogueOutlinedButton

/** Play on TV on a title page: why it can't be pressed ([reason], `null` when it can), and [cast], which offers a title from a second. */
class PlayOnTv(
    val reason: String?,
    val cast: (setId: String, pos: Double) -> Unit,
)

/** Play on TV as [CastSenderViewModel] has it; what the TV answers is [CastAskHost]'s. */
@Composable
internal fun rememberPlayOnTv(sender: CastSenderViewModel = hiltViewModel()): PlayOnTv {
    val reason by sender.castable.collectAsStateWithLifecycle()
    return PlayOnTv(reason) { setId, pos -> sender.playOnTv(Playback(setId, playing = true, pos = pos, rate = 1.0)) }
}

/**
 * The pill after Play or Resume, the web's `castPill`: [setId] from [pos],
 * the point that pill would start at. Outlined, as My List is; greyed with
 * the reason, which a screen reader hears after its name.
 */
@Composable
internal fun PlayOnTvPill(
    playOnTv: PlayOnTv,
    setId: String,
    pos: Double,
) {
    val words = listOfNotNull(CastWords.PLAY_ON_TV, playOnTv.reason).joinToString(", ")
    CatalogueOutlinedButton(
        onClick = { playOnTv.cast(setId, pos) },
        enabled = playOnTv.reason == null,
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).semantics { contentDescription = words },
    ) {
        Text(CastWords.PLAY_ON_TV)
    }
}
