package ui.profile

import catalog.profile.ManageProfilesViewModel

/**
 * What Manage profiles can ask for — the phone's screen and the television's
 * answer the same seven, so they are one bundle here: [ManageProfilesViewModel]'s
 * actions ([manageActions]), or a test's record of them.
 */
class ManageActions(
    val onActAs: (id: String) -> Unit,
    val onAddKid: (name: String, age: Int) -> Unit,
    val onSetKidsAge: (id: String, age: Int) -> Unit,
    val onRemove: (id: String) -> Unit,
    val onAddGrownUp: (name: String) -> Unit,
    val onChangePin: (id: String) -> Unit,
    val onClose: () -> Unit,
)

fun ManageProfilesViewModel.manageActions(): ManageActions =
    ManageActions(::actAs, ::addKid, ::setKidsAge, ::remove, ::addGrownUp, ::changePin, ::close)
