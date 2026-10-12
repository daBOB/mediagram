package ui.common.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.Entry
import catalog.TogetherOffer
import catalog.playsIn
import kotlinx.coroutines.flow.StateFlow

/** Watch together's Join where a viewer looks for something to watch: the session's [offer], and what Join does. */
class TogetherJoin(
    val offer: TogetherOffer,
    val join: () -> Unit,
)

/** Where Join comes from: the live [offers], and what joining one does. */
class TogetherOffers(
    val offers: StateFlow<TogetherOffer?>,
    val join: (TogetherOffer) -> Unit,
)

/**
 * The library's [TogetherOffers], provided once at its root. Providing them
 * listens to nothing: only a screen that draws Join collects them
 * ([togetherJoin]), so the sessions are listened for while Home or a
 * title page is on screen, and not on a department tab or in the player.
 */
val LocalTogetherOffers = compositionLocalOf<TogetherOffers?> { null }

/** [catalog]'s offers; [open] opens the session's title as the frame's own Play does, and the player it opens joins. */
@Composable
fun rememberTogetherOffers(
    catalog: CatalogViewModel,
    open: (setId: String) -> Unit,
): TogetherOffers {
    val opens by rememberUpdatedState(open)
    return remember(catalog) {
        TogetherOffers(catalog.together) { offer ->
            catalog.join(offer)
            opens(offer.set.setId)
        }
    }
}

/** The Join to draw here, `null` with no session this profile may join. Calling it is what listens. */
@Composable
fun togetherJoin(): TogetherJoin? {
    val source = LocalTogetherOffers.current ?: return null
    val offer by source.offers.collectAsStateWithLifecycle()
    return remember(source, offer) { offer?.let { live -> TogetherJoin(live) { source.join(live) } } }
}

/** Join on a film's page: while the session plays [setId]. */
@Composable
fun togetherJoinOf(setId: String): TogetherJoin? = togetherJoin()?.takeIf { it.offer.set.setId == setId }

/** Join on a show's or a course's page: while the session plays one of [collection]'s titles. */
@Composable
fun togetherJoinIn(collection: Entry.Collection): TogetherJoin? =
    togetherJoin()?.takeIf { it.offer.playsIn(collection) }
