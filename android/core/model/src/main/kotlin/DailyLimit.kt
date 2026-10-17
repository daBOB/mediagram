package model

/** At or under this much left, a kid is told once — the web's `WARN_SECONDS`. */
const val DAILY_WARN_SECONDS: Double = 300.0

/** The words of the daily limit, the web's `DAILY_LIMIT_WORDS`, held to `daily-limit.json`. */
object DailyLimitWords {
    const val LABEL = "Daily limit"
    const val WARNING = "Only a few minutes of watching left today. This one can finish."
    const val OUT = "That's all the watching for today. See you tomorrow."
}

/** One entry of the daily-limit menu; [minutes] null is no limit. */
data class DailyLimitChoice(
    val minutes: Int?,
    val label: String,
)

/** What Manage profiles offers, in order — the web's `DAILY_LIMIT_CHOICES`. */
val DAILY_LIMIT_CHOICES: List<DailyLimitChoice> =
    listOf(
        DailyLimitChoice(null, "No limit"),
        DailyLimitChoice(30, "30 min"),
        DailyLimitChoice(45, "45 min"),
        DailyLimitChoice(60, "1 h"),
        DailyLimitChoice(90, "1 h 30 min"),
        DailyLimitChoice(120, "2 h"),
        DailyLimitChoice(180, "3 h"),
    )

/** The menu entry for [minutes]; a limit set elsewhere that is not on the menu shows as itself, as the web's select does. */
fun dailyLimitChoiceFor(minutes: Int?): DailyLimitChoice =
    DAILY_LIMIT_CHOICES.find { it.minutes == minutes } ?: DailyLimitChoice(minutes, "$minutes min")

enum class DailyLimitState { FREE, OK, WARN, OUT }

/** [leftSeconds] never goes below 0, and is null when [state] is [DailyLimitState.FREE]. */
data class DailyLimitStatus(
    val state: DailyLimitState,
    val leftSeconds: Double?,
)

/** The web's `dailyLimitState`: free with no limit, else ok, warn (5 minutes or less left) or out. */
fun dailyLimitState(
    limitMinutes: Int?,
    watchedSeconds: Double,
): DailyLimitStatus {
    if (limitMinutes == null) return DailyLimitStatus(DailyLimitState.FREE, null)
    val left = maxOf(0.0, limitMinutes * 60.0 - watchedSeconds)
    val state =
        when {
            left <= 0.0 -> DailyLimitState.OUT
            left <= DAILY_WARN_SECONDS -> DailyLimitState.WARN
            else -> DailyLimitState.OK
        }
    return DailyLimitStatus(state, left)
}
