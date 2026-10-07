package ui.catalog

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import data.PortraitRequestLog
import kotlinx.coroutines.CancellationException

/**
 * A person's portrait, fetched lazily and at most once per session when
 * [known] is `null`. [portraits] owns the "once": it is held above this
 * Composable, so it survives this screen closing and reopening, not just
 * this recomposition. A fetch cut short — a cast row scrolled away, a
 * screen left mid-request — never finishes, so whichever card asks next
 * fetches again.
 *
 * A card that leaves composition and returns — scrolled out of a `LazyRow`
 * and back, a tab switched away from and back to — starts a fresh `remember`
 * with [known] still `null`, and the log already says no fetch is needed.
 * The path seeded from [PortraitRequestLog.pathOf] is what keeps the
 * portrait a finished fetch found on screen then, rather than initials for
 * the rest of the session. A person confirmed to have none is `null` again
 * on remount, which is correct.
 */
@Composable
fun rememberPortrait(
    personId: Long,
    known: String?,
    portraits: PortraitRequestLog,
    fetch: suspend (Long) -> String?,
): String? {
    var path by remember(personId, known) { mutableStateOf(known ?: portraits.pathOf(personId)) }
    LaunchedEffect(personId, known) {
        if (known != null || !portraits.needsFetch(personId)) return@LaunchedEffect
        try {
            val fetched = fetch(personId)
            path = fetched
            portraits.finish(personId, fetched)
        } catch (e: CancellationException) {
            throw e // cut short, not finished: retried next time something asks
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            portraits.finish(personId, null)
            Log.w("CatalogMetadata", "Could not fetch a portrait", e)
        }
    }
    return path
}
