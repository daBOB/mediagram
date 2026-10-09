package catalog

import data.LiveSession
import data.TogetherLive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import model.MediaSet

/**
 * A live watch-together session this profile may join: its channel message
 * [id], the title it plays, and who is in it, as the web names them
 * ("TV, Tablet").
 */
data class TogetherOffer(
    val id: Int,
    val set: MediaSet,
    val members: String,
)

/** Home's notice, in the web's words (`together-join.js`): "Watching together: Title · TV, Tablet". */
val TogetherOffer.line: String
    get() = "Watching together: ${set.show?.let { "$it ${episodeShort(set)}" } ?: set.title} · $members"

/** Whether the session plays one of [collection]'s titles: a show's or a course's page offers Join then. */
fun TogetherOffer.playsIn(collection: Entry.Collection): Boolean = playableInOrder(collection.divisions).any { it.setId == set.setId }

/**
 * The offer [catalog] makes as [live] changes. Each time someone starts
 * showing it the sessions are read again: nothing was heard while nobody
 * listened, and the web reads them as its stream opens.
 */
internal fun togetherOffers(
    live: TogetherLive,
    catalog: Flow<CatalogUiState>,
): Flow<TogetherOffer?> =
    channelFlow {
        launch { live.refresh() }
        combine(live.live, live.available, catalog, ::offerOf).distinctUntilChanged().collect(::send)
    }

private fun offerOf(
    live: LiveSession?,
    available: Boolean,
    catalog: CatalogUiState,
): TogetherOffer? {
    // Hidden, not greyed, out of Telegram's reach: no session can be known then.
    val held = live?.takeIf { available } ?: return null
    // The projected catalog, so a kids profile is never offered a title past its limit.
    val set = catalog.mediaSet(held.session.set) ?: return null
    return TogetherOffer(held.id, set, held.session.members.joinToString(", ") { it.label })
}
