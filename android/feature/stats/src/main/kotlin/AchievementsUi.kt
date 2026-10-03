package stats

import uniffi.mediagram_core.Achievements
import java.time.ZonedDateTime

/**
 * The Stats page's Achievements section, every string finished — the web's
 * `achievementsSection` (`web/public/lib/catalog/stats-achievements.js`).
 * [achievementLabel] and [progressLine] are held to that file by
 * `achievement-labels.json`.
 */
data class AchievementsUi(
    val earned: List<EarnedLine>,
    val next: List<NextLine>,
) {
    companion object {
        val None = AchievementsUi(emptyList(), emptyList())
    }
}

/** One achievement earned: its name, and the day it was earned. */
data class EarnedLine(
    val id: String,
    val label: String,
    val on: String,
)

/** One still to come: its name, how far along in words, and as a share for the bar. */
data class NextLine(
    val id: String,
    val label: String,
    val progress: String,
    val fraction: Float,
)

/** What a screen reader hears for the dot on the rail's Stats row. */
const val NEW_ACHIEVEMENT = "New achievement"

/** Nothing earned and nothing to come. */
val NO_ACHIEVEMENTS = Achievements(earned = emptyList(), next = emptyList())

private val RUNG = Regex("([a-z]+)-(\\d+)")
private val CLOCK = Regex(" \\d{2}:\\d{2}$")

/** An achievement's name. An id this build does not know shows as itself rather than vanishing. */
fun achievementLabel(id: String): String {
    if (id == "whole-show") return "A whole series"
    val (ladder, n) = RUNG.matchEntire(id)?.destructured ?: return id
    return when (ladder) {
        "films" -> if (n == "1") "First film" else "$n films"
        "genres" -> "$n genres"
        "docs" -> "$n documentaries"
        "hours" -> "$n hours"
        "streak" -> "$n-day streak"
        "binge" -> "$n episodes in a day"
        else -> id
    }
}

/**
 * How far along one still to come is: "7 of 10 films". A whole series names
 * no unit — its count is a show's episodes as often as a course's lessons.
 */
fun progressLine(
    id: String,
    have: UInt,
    need: UInt,
): String {
    val unit =
        when (RUNG.matchEntire(id)?.groupValues?.get(1)) {
            "films" -> if (need == 1u) "film" else "films"
            "genres" -> "genres"
            "docs" -> "documentaries"
            "hours" -> "hours"
            "streak" -> "days"
            "binge" -> "episodes"
            else -> null
        }
    return if (unit == null) "$have of $need" else "$have of $need $unit"
}

/**
 * The day an achievement was earned: [whenText] without its clock time. A
 * day-based achievement is dated at that day's local midnight, and "Sat
 * 00:00" would read as a moment rather than a day.
 */
fun earnedOn(
    atMs: Long,
    now: ZonedDateTime,
): String = whenText(atMs, now).replace(CLOCK, "")

/**
 * This device's offset from UTC at [at], in minutes — the one in force at
 * that moment, so a clock change is taken as it happens. What the core
 * places a day-based achievement's midnight by.
 */
fun utcOffsetMinutes(at: ZonedDateTime): Int = at.offset.totalSeconds / 60

/** The section for [achievements], every day told on [now]'s clock. */
fun achievementsUiOf(
    achievements: Achievements,
    now: ZonedDateTime,
): AchievementsUi =
    AchievementsUi(
        earned = achievements.earned.map { EarnedLine(it.id, achievementLabel(it.id), earnedOn(it.earnedAt, now)) },
        next =
            achievements.next.map {
                NextLine(it.id, achievementLabel(it.id), progressLine(it.id, it.have, it.need), it.have.toFloat() / it.need.toFloat())
            },
    )
