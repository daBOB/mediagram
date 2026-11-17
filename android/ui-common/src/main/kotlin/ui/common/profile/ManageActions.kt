package ui.common.profile

import catalog.profile.ManageProfilesViewModel
import catalog.profile.addGrownUp
import catalog.profile.addKid

/**
 * What Manage profiles can ask for — the phone's screen and the television's
 * answer the same nine, so they are one bundle here: [ManageProfilesViewModel]'s
 * actions ([manageActions]), or a test's record of them.
 */
class ManageActions(
    val onActAs: (id: String) -> Unit,
    /** [parentId]: one of the part's [catalog.profile.ManagePart.kidParents], or null for whoever adds it. */
    val onAddKid: (name: String, age: Int, dailyLimit: Int?, parentId: String?) -> Unit,
    val onSetKidsAge: (id: String, age: Int) -> Unit,
    val onSetDailyLimit: (id: String, minutes: Int?) -> Unit,
    val onSetHiddenSections: (id: String, sections: List<String>) -> Unit,
    val onRemove: (id: String) -> Unit,
    /** [household]: the part's id, null for the maker's own. */
    val onAddGrownUp: (name: String, household: String?) -> Unit,
    val onChangePin: (id: String) -> Unit,
    val onClose: () -> Unit,
)

/** [onClose] is Done and Back: Manage's own close, unless what opened it closes it — Settings' panel, which drops the PIN as it goes. */
fun ManageProfilesViewModel.manageActions(onClose: () -> Unit = ::close): ManageActions =
    ManageActions(::actAs, this::addKid, ::setKidsAge, ::setDailyLimit, ::setHiddenSections, ::remove, this::addGrownUp, ::changePin, onClose)
