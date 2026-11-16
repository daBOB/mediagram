package catalog.profile

import model.ProfileRequest

/** [name] trimmed; a blank one is not sent, as the web's form sends none. */
fun ManageProfilesViewModel.addKid(
    name: String,
    age: Int = NEW_KID_LIMIT,
    dailyLimit: Int? = null,
) {
    val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
    change { actor, pin -> ProfileRequest.CreateKid(actor, pin, clean, age, dailyLimit) }
}

/** Admin only; the new grown-up's first PIN is typed twice before anything is sent. */
fun ManageProfilesViewModel.addGrownUp(name: String) {
    val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
    newPin(newPinTitleFor(clean), own = false) { actor, pin, newPin -> ProfileRequest.CreateGrownUp(actor, pin, clean, newPin) }
}
