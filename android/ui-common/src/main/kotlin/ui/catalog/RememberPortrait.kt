package ui.catalog

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/**
 * A person's portrait, fetched lazily and at most once per session when
 * [known] is `null`. [shouldRequest] is `data.PortraitRequestLog.shouldRequest`,
 * held above this Composable so the "once" survives this screen closing and
 * reopening, not just this recomposition.
 *
 * [shouldRequest] reserves a person the moment a fetch is attempted, before
 * the attempt finishes — a cast row scrolled out of view (or a screen left)
 * while its fetch is still in flight cancels that attempt, but the shared
 * log has already marked the person asked, with nothing to show for it. A
 * second, purely local [donePortraitFetches] records which reservations
 * actually ran to completion (found a portrait or not); one that never did
 * is retried here the next time something asks, even though the shared log
 * itself still says no. Both sets are keyed by [personId] alone, so a person
 * reserved by one screen and abandoned mid-fetch is retried by whichever
 * screen next asks for them, not only the one that first tried.
 *
 * A path a fetch actually found is also kept in [portraitPaths], separately
 * from the two sets above: [path] itself lives in `remember`, scoped to
 * this call site's own composition, so a card that leaves composition and
 * returns later — scrolled out of a `LazyRow` and back, a tab switched away
 * from and back to — starts a fresh `remember` with [known] still `null`.
 * Without [portraitPaths] that fresh start had nothing to seed [path] from,
 * and [shouldRequest] already says no for a person the shared log marked
 * asked, so the portrait a fetch already found would show as initials for
 * the rest of the session, not just as a brief re-fetch flicker. Only found
 * paths are kept — a person confirmed to have none is correctly `null`
 * again on remount, and re-showing initials for them is not the bug this
 * guards against.
 */
@Composable
fun rememberPortrait(
    personId: Long,
    known: String?,
    shouldRequest: (Long) -> Boolean,
    fetch: suspend (Long) -> String?,
): String? {
    var path by remember(personId, known) { mutableStateOf(known ?: portraitPaths[personId]) }
    LaunchedEffect(personId, known) {
        if (known != null) return@LaunchedEffect
        val retrying = personId in attemptedPortraitFetches && personId !in donePortraitFetches
        if (!retrying && !shouldRequest(personId)) return@LaunchedEffect
        attemptedPortraitFetches += personId
        try {
            val fetched = fetch(personId)
            path = fetched
            if (fetched != null) portraitPaths[personId] = fetched
            donePortraitFetches += personId
        } catch (e: CancellationException) {
            throw e // cut short, not done: retried next time something asks
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            donePortraitFetches += personId
            Log.w("CatalogMetadata", "Could not fetch a portrait", e)
        }
    }
    return path
}

/** Every person [rememberPortrait] has ever started a fetch for this session, whether or not it finished. */
private val attemptedPortraitFetches = ConcurrentHashMap.newKeySet<Long>()

/** Every person [rememberPortrait] has finished a fetch for this session — successfully or not, but never cut short. */
private val donePortraitFetches = ConcurrentHashMap.newKeySet<Long>()

/** Every person [rememberPortrait] has found a real portrait path for this session, surviving a card leaving and re-entering composition. */
private val portraitPaths = ConcurrentHashMap<Long, String>()
