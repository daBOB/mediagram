package catalog.profile

import dagger.hilt.android.scopes.ActivityRetainedScoped
import data.WatchStateRepository
import model.HouseholdWords
import model.Profile
import model.ProfileRequest
import javax.inject.Inject

/**
 * The owner's PIN, held once for the whole Profiles page: asked at the first
 * change by either view model, then sent with every change of both. Dropped
 * when the page is left or a change is answered with a wrong PIN.
 */
@ActivityRetainedScoped
class OwnerPin @Inject constructor() {
    var held: String? = null

    fun drop() {
        held = null
    }
}

/**
 * [then] with the owner's PIN: the one [pin] holds, or — none held — [owner]'s
 * asked now, checked by the core, and held from then on. Nothing without an owner.
 */
internal fun PinAsk.ownersPin(
    pin: OwnerPin,
    repository: WatchStateRepository,
    owner: Profile?,
    then: (String) -> Unit,
) {
    pin.held?.let { return then(it) }
    val boss = owner ?: return
    ask(
        PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false),
        send = { given -> repository.manage(ProfileRequest.Unlock(boss.id, given)) },
        done = { given ->
            pin.held = given
            then(given)
        },
    )
}
