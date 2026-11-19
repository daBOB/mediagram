package catalog.profile

import model.HouseholdWords
import model.ProfileOutcome

/** [visit] counts closes, so an answer to a change sent before one stays with its own visit. */
internal data class OwnerStep(val notice: String? = null, val answers: Int = 0, val sending: Boolean = false, val visit: Int = 0)

/** What an answer to a household change says: nothing when it took. */
internal fun noticeFor(outcome: ProfileOutcome?, named: Boolean): String? =
    when {
        outcome == ProfileOutcome.Done -> null
        outcome == ProfileOutcome.NameTaken && named -> HouseholdWords.HOUSEHOLD_NAME_TAKEN
        else -> outcome.reason()
    }
