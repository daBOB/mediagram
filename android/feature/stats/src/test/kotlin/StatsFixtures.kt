package stats

import uniffi.mediagram_core.DayBar
import uniffi.mediagram_core.HistoryEntry
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import java.time.ZoneId
import java.time.ZonedDateTime

internal val Berlin: ZoneId = ZoneId.of("Europe/Berlin")

/** Saturday 26 September 2026, 22:00 in Berlin — every test's "now". */
internal val Now: ZonedDateTime = ZonedDateTime.of(2026, 9, 26, 22, 0, 0, 0, Berlin)

/** Epoch milliseconds of a Berlin wall-clock time. */
internal fun ms(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, Berlin).toInstant().toEpochMilli()

/** Thirty days ending on [Now]'s date, oldest first, as the core answers them; [today] and [yesterday] fill the last two. */
internal fun last30(
    today: Double = 0.0,
    yesterday: Double = 0.0,
): List<DayBar> =
    (29 downTo 0).map { back ->
        val seconds =
            when (back) {
                0 -> today
                1 -> yesterday
                else -> 0.0
            }
        DayBar(day = Now.toLocalDate().minusDays(back.toLong()).toString(), seconds = seconds)
    }

internal fun entry(
    kind: HistoryKind,
    setId: String,
    at: Long,
    seconds: Double,
) = HistoryEntry(kind = kind, setId = setId, at = at, seconds = seconds)

internal fun summary(
    week: Double = 0.0,
    month: Double = 0.0,
    all: Double = 0.0,
    days: List<DayBar> = last30(),
    history: List<HistoryEntry> = emptyList(),
) = StatsSummary(weekSeconds = week, monthSeconds = month, allSeconds = all, last30 = days, history = history)
