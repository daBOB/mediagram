package catalog

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import model.together.PlaybackChange
import java.util.UUID

/** Names the cast bar as the screen acting, so its moves come from no member of the session. */
private val barScreen = UUID.randomUUID().toString()

/** The cast bar's play/pause: a move on [offer]'s session at its expected position, from no member of it. */
fun CatalogViewModel.playPause(offer: TogetherOffer) {
    viewModelScope.launch { togetherLive.act(offer.id, barScreen, PlaybackChange(playing = !offer.playing)) }
}
