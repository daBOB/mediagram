package catalog.profile

import model.Households
import model.Profile
import model.ProfileOutcome

/** Where Manage profiles is: closed, asking who is acting, or managing as one grown-up. */
internal sealed interface ManageStep {
    data object Closed : ManageStep

    data class ChoosingActor(val notice: String? = null) : ManageStep

    data class Managing(val actorId: String, val notice: String? = null, val answers: Int = 0) : ManageStep
}

internal fun ManageStep.shown(profiles: List<Profile>, install: Households): ManageUiState =
    when (this) {
        ManageStep.Closed -> ManageUiState.Closed
        is ManageStep.ChoosingActor -> profiles.filterNot { it.kids }.let { here -> ManageUiState.ChoosingActor(here, notice, install.owner ?: here.find { it.owner }) }
        is ManageStep.Managing -> manageable(profiles, actorId, notice, answers, install)
    }

/**
 * Where a change answered with [outcome] leaves Manage — the web's `report`.
 * Every change sends the PIN held, so a wrong one means it is no longer
 * theirs: ask who they are again rather than spend that profile's tries — but
 * the owner's view ([ownersView]) stays, and asks the owner's PIN again at the
 * next change. Each answer is counted, so even a refusal already on show — a
 * new try clears it first — is news to a control waiting on its answer.
 */
internal fun ManageStep.Managing.answered(
    outcome: ProfileOutcome?,
    ownersView: Boolean,
): ManageStep =
    when {
        outcome == ProfileOutcome.Done -> ManageStep.Managing(actorId, answers = answers + 1)
        outcome == ProfileOutcome.WrongPin && !ownersView -> ManageStep.ChoosingActor(PIN_NO_LONGER_VALID)
        else -> ManageStep.Managing(actorId, outcome.reason(), answers + 1)
    }
