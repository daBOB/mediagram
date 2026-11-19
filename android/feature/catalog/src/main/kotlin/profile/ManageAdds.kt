package catalog.profile

import model.ProfileRequest

/**
 * [name] trimmed; a blank one is not sent, as the web's form sends none.
 * [parentId] is the grown-up it belongs to, one of its part's
 * [ManagePart.kidParents]; null is whoever adds it.
 */
fun ManageProfilesViewModel.addKid(
    name: String,
    age: Int = NEW_KID_LIMIT,
    dailyLimit: Int? = null,
    parentId: String? = null,
) {
    val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
    change { actor, pin -> ProfileRequest.CreateKid(actor, pin, clean, age, dailyLimit, parentId) }
}

/**
 * Admin only, into [household] — its part's id, null for the maker's own; the
 * new grown-up's first PIN is typed twice before anything is sent.
 */
fun ManageProfilesViewModel.addGrownUp(
    name: String,
    household: String? = null,
) {
    val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
    newPin(newPinTitleFor(clean), own = false) { actor, pin, newPin -> ProfileRequest.CreateGrownUp(actor, pin, clean, newPin, household) }
}
