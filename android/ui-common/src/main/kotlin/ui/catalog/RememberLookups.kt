package ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import data.orDefault
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
): TitleInfo? = rememberLookup(posterKey, null, "title details lookup") { key -> key?.let { lookup(it) } }.first

/**
 * A title's cast and crew, looked up once per key — the same shape as
 * [rememberTitleInfo], for the Cast tab that only appears once credits
 * arrive and name somebody.
 */
@Composable
fun rememberTitleCredits(
    key: String?,
    lookup: suspend (String) -> TitleCredits,
): TitleCredits = rememberLookup(key, TitleCredits.Empty, "credits lookup") { k -> k?.let { lookup(it) } ?: TitleCredits.Empty }.first

/** [rememberPersonLookup]'s answer, plus whether it is still on its way. */
data class PersonLookup(val person: Person?, val loading: Boolean)

/**
 * One person, looked up once per id — the same shape as [rememberTitleInfo],
 * with [PersonLookup.loading] alongside its answer: a person page needs the
 * two told apart, since nobody by that id and "still asking" both start as
 * a `null` [Person], and only one of them is the page's own empty sentence
 * to show.
 */
@Composable
fun rememberPersonLookup(
    personId: Long,
    lookup: suspend (Long) -> Person?,
): PersonLookup {
    val (person, loading) = rememberLookup(personId, null, "person lookup", lookup)
    return PersonLookup(person, loading)
}

/**
 * Every franchise's TMDB overview, fetched once for as long as this
 * Composable stays in the tree — the Collections tab and a franchise page
 * both read the same list rather than each asking the index again.
 */
@Composable
fun rememberFranchiseOverviews(lookup: suspend () -> List<FranchiseInfo>): List<FranchiseInfo> =
    rememberLookup(Unit, emptyList(), "franchise overviews lookup") { lookup() }.first

/**
 * [lookup]'s answer for [key], and whether it is still on its way. Starts
 * at [initial] for each key; a failure is logged as "[what] failed" and
 * leaves [initial] in place, and a cancellation stays a cancellation.
 */
@Composable
private fun <K, T> rememberLookup(
    key: K,
    initial: T,
    what: String,
    lookup: suspend (K) -> T,
): Pair<T, Boolean> {
    var value by remember(key) { mutableStateOf(initial) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        value = orDefault(initial, what) { lookup(key) }
        loading = false
    }
    return value to loading
}
