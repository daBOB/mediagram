package stats

import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.StatsSummary
import java.time.LocalDate
import java.time.ZonedDateTime

/** Under the heading when the profile's history is empty. */
const val NOTHING_WATCHED = "Nothing watched yet."

/** The page's headings, the web's words — one copy for the phone's page and the television's. */
const val STATS_HEADING = "Stats"
const val LAST_30_DAYS_HEADING = "Last 30 days"
const val ACHIEVEMENTS_HEADING = "Achievements"
const val NEXT_HEADING = "Next"

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
        /** What the profile has earned and the closest to come, read at the same moment. */
        val achievements: Achievements = NO_ACHIEVEMENTS,
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
        /** What was earned and what is next; [AchievementsUi.None] draws no section. */
        val achievements: AchievementsUi = AchievementsUi.None,
        /** "Counting since 3 Oct", the line under the totals while nothing is counted yet; `null` draws none. */
        val countingSince: String? = null,
    ) : StatsUiState
}

/** One day's bar: its height as a share of the busiest day's, its weekday letter, and its label ("3 Oct · 42 min"). */
data class StatsBar(
    val fraction: Float,
    val initial: String,
    val description: String,
)

/** The page for [read]. */
fun statsUiStateOf(read: StatsRead): StatsUiState =
    when (read) {
        StatsRead.Loading -> StatsUiState.Loading
        is StatsRead.Failed -> StatsUiState.Failed(failureLine(read.reason))
        is StatsRead.Done ->
            if (read.summary.history.isEmpty()) {
                StatsUiState.Empty
            } else {
                pageOf(read.summary, read.now).copy(achievements = achievementsUiOf(read.achievements, read.now))
            }
    }

private fun pageOf(
    summary: StatsSummary,
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
        countingSince = countingSinceText(summary, now),
    )
}
