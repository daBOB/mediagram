package ui.common.profile

import catalog.profile.ManageProfilesViewModel

/**
 * What Manage profiles can ask for — the phone's screen and the television's
 * answer the same nine, so they are one bundle here: [ManageProfilesViewModel]'s
 * actions ([manageActions]), or a test's record of them.
 */
class ManageActions(
    val onActAs: (id: String) -> Unit,
    val onAddKid: (name: String, age: Int, dailyLimit: Int?) -> Unit,
    val onSetKidsAge: (id: String, age: Int) -> Unit,
    val onSetDailyLimit: (id: String, minutes: Int?) -> Unit,
    val onSetHiddenSections: (id: String, sections: List<String>) -> Unit,
    val onRemove: (id: String) -> Unit,
    val onAddGrownUp: (name: String) -> Unit,
    val onChangePin: (id: String) -> Unit,
    val onClose: () -> Unit,
)

/** [onClose] is Done and Back: Manage's own close, unless what opened it closes it — Settings' panel, which drops the PIN as it goes. */
fun ManageProfilesViewModel.manageActions(onClose: () -> Unit = ::close): ManageActions =
    ManageActions(::actAs, ::addKid, ::setKidsAge, ::setDailyLimit, ::setHiddenSections, ::remove, ::addGrownUp, ::changePin, onClose)
