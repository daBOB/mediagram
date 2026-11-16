package catalog.profile

import model.Households
import model.Profile

/** Where Manage profiles is: closed, asking who is acting, or managing as one grown-up. */
internal sealed interface ManageStep {
    data object Closed : ManageStep

    data class ChoosingActor(val notice: String? = null) : ManageStep

    data class Managing(val actorId: String, val notice: String? = null, val answers: Int = 0) : ManageStep
}

internal fun ManageStep.shown(profiles: List<Profile>, install: Households): ManageUiState =
    when (this) {
        ManageStep.Closed -> ManageUiState.Closed
        is ManageStep.ChoosingActor -> ManageUiState.ChoosingActor(profiles.filterNot { it.kids }, notice)
        is ManageStep.Managing -> manageable(profiles, actorId, notice, answers, install)
    }
