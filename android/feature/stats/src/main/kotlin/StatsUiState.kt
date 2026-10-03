package stats

import model.Kind
import model.MediaSet
import model.episodeLabel
import uniffi.mediagram_core.HistoryEntry
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import java.time.LocalDate
import java.time.ZonedDateTime

/** Under the heading when the profile's history is empty. */
const val NOTHING_WATCHED = "Nothing watched yet."

/** A history line's title for a set this profile's catalogue does not hold. */
const val NO_LONGER_IN_LIBRARY = "No longer in the library"

/** What the core answered for the chosen profile, before any set is named — [StatsViewModel]'s state. */
sealed interface StatsRead {
    data object Loading : StatsRead

    /** The read could not be made; [reason] as the platform gave it, if it gave one. */
    data class Failed(
        val reason: String?,
    ) : StatsRead

    /** The profile's summary, read at [now]: its date was the read's "today", and its clock tells every line's time. */
    data class Done(
        val summary: StatsSummary,
        val now: ZonedDateTime,
    ) : StatsRead
}

/** The Stats page, every string finished, so both surfaces render the same words. */
sealed interface StatsUiState {
    data object Loading : StatsUiState

    data class Failed(
        val text: String,
    ) : StatsUiState

    /** Nothing in the history: only the heading and [NOTHING_WATCHED] — no zero totals, no empty chart. */
    data object Empty : StatsUiState

    data class Ready(
        /** "This week", "This month", "All time", each with its duration. */
        val totals: List<Pair<String, String>>,
        /** Thirty days, oldest first, today last. */
        val bars: List<StatsBar>,
        /** Newest first, in the core's order. */
        val history: List<StatsLine>,
    ) : StatsUiState
}

/** One day's bar: its height as a share of the busiest day's, its weekday letter, and its label ("3 Oct · 42 min"). */
data class StatsBar(
    val fraction: Float,
    val initial: String,
    val description: String,
)

/** One history line. [key] is unique on the page: a set has at most one line of each kind. */
data class StatsLine(
    val key: String,
    val text: String,
)

/**
 * The page for [read], every set named from [sets]: this profile's own
 * catalogue by set id, so a title this profile cannot see is never named.
 * [sets] is `null` while that catalogue is still loading; a page with
 * history waits for it rather than calling every title gone.
 */
fun statsUiStateOf(
    read: StatsRead,
    sets: Map<String, MediaSet>?,
): StatsUiState =
    when (read) {
        StatsRead.Loading -> StatsUiState.Loading
        is StatsRead.Failed -> StatsUiState.Failed(failureLine(read.reason))
        is StatsRead.Done ->
            when {
                read.summary.history.isEmpty() -> StatsUiState.Empty
                sets == null -> StatsUiState.Loading
                else -> pageOf(read.summary, sets, read.now)
            }
    }

private fun pageOf(
    summary: StatsSummary,
    sets: Map<String, MediaSet>,
    now: ZonedDateTime,
): StatsUiState.Ready {
    val busiest = summary.last30.maxOfOrNull { it.seconds } ?: 0.0
    return StatsUiState.Ready(
        totals =
            listOf(
                "This week" to durationText(summary.weekSeconds),
                "This month" to durationText(summary.monthSeconds),
                "All time" to durationText(summary.allSeconds),
            ),
        bars =
            summary.last30.map { bar ->
                val date = LocalDate.parse(bar.day)
                StatsBar(
                    fraction = if (busiest > 0) (bar.seconds / busiest).toFloat().coerceIn(0f, 1f) else 0f,
                    initial = weekdayInitial(date),
                    description = "${shortDate(date)} · ${durationText(bar.seconds)}",
                )
            },
        history = summary.history.map { entry -> StatsLine(key = "${entry.kind}:${entry.setId}", text = historyLine(entry, sets[entry.setId], now)) },
    )
}

/**
 * One history line: "Started · Der Pate · Sat 21:14 · 42 min". No duration
 * when nothing was counted: a finish from before stats existed was not
 * watched in under a minute.
 */
fun historyLine(
    entry: HistoryEntry,
    set: MediaSet?,
    now: ZonedDateTime,
): String {
    val parts = mutableListOf(kindLabel(entry.kind), statsTitle(set), whenText(entry.at, now))
    if (entry.seconds > 0) parts += durationText(entry.seconds)
    return parts.joinToString(" · ")
}

fun kindLabel(kind: HistoryKind): String =
    when (kind) {
        HistoryKind.STARTED -> "Started"
        HistoryKind.FINISHED -> "Finished"
        HistoryKind.AGAIN -> "Watched again"
    }

/**
 * The library's own name for a set, the way Continue watching names it: a
 * film by its title, an episode or lesson by its show and number ("Crime
 * 101 S1E4", "Geldhochschule 3"). A set this profile's catalogue does not
 * hold — gone, or not for this profile — is not named.
 */
fun statsTitle(set: MediaSet?): String {
    if (set == null) return NO_LONGER_IN_LIBRARY
    val show = set.show?.takeIf { it.isNotEmpty() }
    if (set.kind == Kind.MOVIE || show == null) return set.title
    return listOf(show, episodeLabel(set)).filter { it.isNotEmpty() }.joinToString(" ")
}
