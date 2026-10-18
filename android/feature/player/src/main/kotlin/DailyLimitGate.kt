package player

import data.WatchStateRepository
import data.orDefault
import model.DailyLimitState
import model.Profile
import model.dailyLimitState

/**
 * Whether a title may open for the profile watching — the daily limit's one
 * question, asked wherever a title is about to open (the library, and the
 * player's own switch to the next).
 */
interface PlayGate {
    /** Whether the profile watching has a daily limit at all; everyone else opens without being asked. */
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
 * not a lock — a core that cannot answer lets the title open. [profile] is
 * who is watching: the chosen profile, or a cast's caster.
 */
class DailyLimitGate(
    private val repository: WatchStateRepository,
    private val profile: () -> Profile? = { repository.chosenProfile.value },
) : PlayGate {
    override fun applies(): Boolean = profile()?.let { it.kids && it.dailyLimit != null } == true

    /** Where today stands, or null for a profile without a limit and when the core cannot say. */
    suspend fun standing(): DailyLimitState? {
        val kid = profile()?.takeIf { it.kids } ?: return null
        val limit = kid.dailyLimit ?: return null
        val watched = orDefault(null, "today's watching") { repository.watchedToday(kid.id) } ?: return null
        return dailyLimitState(limit, watched).state
    }

    override suspend fun mayOpen(): Boolean = standing() != DailyLimitState.OUT
}
