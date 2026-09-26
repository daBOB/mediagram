package ui.catalog

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import model.FranchiseInfo
import model.Person
import model.TitleCredits
import uniffi.mediagram_core.TitleInfo

/**
 * What the index records about the title a poster key names, looked up once
 * per key.
 *
 * Null both while the answer is on its way and when there is no answer, and
 * the screen renders the same either way: a title with no provider entry is
 * the ordinary case, so a spinner over the blocks it would fill would
 * promise something that is never coming.
 */
@Composable
fun rememberTitleInfo(
    posterKey: String?,
    lookup: suspend (String) -> TitleInfo?,
): TitleInfo? {
    var info by remember(posterKey) { mutableStateOf<TitleInfo?>(null) }
    LaunchedEffect(posterKey) {
        try {
            info = posterKey?.let { lookup(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load title details", e)
        }
    }
    return info
}

/**
 * The local file for a poster key, looked up once per key.
 *
 * The same shape as [rememberTitleInfo], generalised to artwork: a wall
 * opens several of these at once, one per plate, where a title screen only
 * ever asks for one synopsis.
 */
@Composable
fun rememberPosterPath(
    key: String?,
    lookup: suspend (String) -> String?,
): String? {
    var path by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        try {
            path = key?.let { lookup(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load season poster", e)
        }
    }
    return path
}

/**
 * A title's cast and crew, looked up once per key — the same shape as
 * [rememberTitleInfo], for the Cast tab that only appears once credits
 * arrive and name somebody.
 */
@Composable
fun rememberTitleCredits(
    key: String?,
    lookup: suspend (String) -> TitleCredits,
): TitleCredits {
    var credits by remember(key) { mutableStateOf(TitleCredits.Empty) }
    LaunchedEffect(key) {
        try {
            credits = key?.let { lookup(it) } ?: TitleCredits.Empty
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load credits", e)
        }
    }
    return credits
}

/** One person, looked up once per id — the same shape as [rememberTitleInfo]. */
@Composable
fun rememberPerson(
    personId: Long,
    lookup: suspend (Long) -> Person?,
): Person? {
    var person by remember(personId) { mutableStateOf<Person?>(null) }
    LaunchedEffect(personId) {
        try {
            person = lookup(personId)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load a person", e)
        }
    }
    return person
}

/** [rememberPerson]'s answer, plus whether it is still on its way. */
data class PersonLookup(val person: Person?, val loading: Boolean)

/**
 * [rememberPerson], with [PersonLookup.loading] alongside its answer — a
 * person page needs the two told apart: nobody by that id and "still
 * asking" both start as a `null` [Person], and only one of them is the page's
 * own empty sentence to show.
 */
@Composable
fun rememberPersonLookup(
    personId: Long,
    lookup: suspend (Long) -> Person?,
): PersonLookup {
    var person by remember(personId) { mutableStateOf<Person?>(null) }
    var loading by remember(personId) { mutableStateOf(true) }
    LaunchedEffect(personId) {
        loading = true
        try {
            person = lookup(personId)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load a person", e)
        } finally {
            loading = false
        }
    }
    return PersonLookup(person, loading)
}

/**
 * Every franchise's TMDB overview, fetched once for as long as this
 * Composable stays in the tree — the Collections tab and a franchise page
 * both read the same list rather than each asking the index again.
 */
@Composable
fun rememberFranchiseOverviews(lookup: suspend () -> List<FranchiseInfo>): List<FranchiseInfo> {
    var overviews by remember { mutableStateOf<List<FranchiseInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            overviews = lookup()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load franchise overviews", e)
        }
    }
    return overviews
}
