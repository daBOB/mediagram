package player

import data.WatchStateRepository
import data.orDefault
import model.DailyLimitState
import model.dailyLimitState

/**
 * Whether a title may open for the chosen profile — the daily limit's one
 * question, asked wherever a title is about to open (the library, and the
 * player's own switch to the next).
 */
interface PlayGate {
    /** Whether the chosen profile has a daily limit at all; everyone else opens without being asked. */
    fun applies(): Boolean

    /** False only when the day's allowance is used up. */
    suspend fun mayOpen(): Boolean

    /** No limit anywhere: for what is built without a profile. */
    object Open : PlayGate {
        override fun applies() = false
        override suspend fun mayOpen() = true
    }
}

/**
 * The web's `daily-limit-gate.js` over the core: watched-today is the core's
 * own count for this device's date, never added up here. A household rule,
 * not a lock — a core that cannot answer lets the title open.
 */
class DailyLimitGate(private val repository: WatchStateRepository) : PlayGate {
    override fun applies(): Boolean = repository.chosenProfile.value?.let { it.kids && it.dailyLimit != null } == true

    /** Where today stands, or null for a profile without a limit and when the core cannot say. */
    suspend fun standing(): DailyLimitState? {
        val profile = repository.chosenProfile.value?.takeIf { it.kids } ?: return null
        val limit = profile.dailyLimit ?: return null
        val watched = orDefault(null, "today's watching") { repository.watchedToday(profile.id) } ?: return null
        return dailyLimitState(limit, watched).state
    }

    override suspend fun mayOpen(): Boolean = standing() != DailyLimitState.OUT
}
