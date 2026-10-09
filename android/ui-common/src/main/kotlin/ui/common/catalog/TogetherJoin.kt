package ui.common.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.Entry
import catalog.TogetherOffer
import catalog.playsIn

/** Watch together's Join where a viewer looks for something to watch: the session's [offer], and what pressing Join does. */
class TogetherJoin(
    val offer: TogetherOffer,
    val join: () -> Unit,
)

/**
 * The Join a frame offers, `null` with no session this profile may join.
 * The frames that show Join provide it ([WithTogetherJoin]) — Home and the
 * title pages, on the phone and the television alike — and it is read where
 * Join is drawn, as the rail reads its own data.
 */
val LocalTogetherJoin = compositionLocalOf<TogetherJoin?> { null }

/**
 * [content] with [catalog]'s Join. [open] opens the session's title as the
 * frame's own Play does, and the player it opens joins. Collected here, so
 * the sessions are listened for only while one of these frames is shown.
 */
@Composable
fun WithTogetherJoin(
    catalog: CatalogViewModel,
    open: (setId: String) -> Unit,
    content: @Composable () -> Unit,
) {
    val offer by catalog.together.collectAsStateWithLifecycle()
    val opens by rememberUpdatedState(open)
    val join =
        remember(offer) {
            offer?.let { live ->
                TogetherJoin(live) {
                    catalog.join(live)
                    opens(live.set.setId)
                }
            }
        }
    CompositionLocalProvider(LocalTogetherJoin provides join, content = content)
}

/** Join on a film's page: while the session plays [setId]. */
@Composable
fun togetherJoinOf(setId: String): TogetherJoin? = LocalTogetherJoin.current?.takeIf { it.offer.set.setId == setId }

/** Join on a show's or a course's page: while the session plays one of [collection]'s titles. */
@Composable
fun togetherJoinIn(collection: Entry.Collection): TogetherJoin? = LocalTogetherJoin.current?.takeIf { it.offer.playsIn(collection) }
