package catalog.profile

import dagger.hilt.android.scopes.ActivityRetainedScoped
import data.WatchStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.HouseholdWords
import model.Profile
import model.ProfileRequest
import javax.inject.Inject

/**
 * The owner's PIN, held once for the whole Profiles page: asked at the first
 * change by either view model, then sent with every change of both. Dropped
 * when the page is left or a change is answered with a wrong PIN. [view] says
 * whether Manage shows the owner's view, whose household steps share the PIN.
 */
@ActivityRetainedScoped
class OwnerPin @Inject constructor() {
    var held: String? = null

    private val showing = MutableStateFlow(false)

    /** Whether Manage shows the owner's view: [ManageProfilesViewModel] sets it with every step it takes, and only it. */
    val view: StateFlow<Boolean> = showing.asStateFlow()

    fun showView(on: Boolean) {
        showing.value = on
    }

    /** A wrong PIN, or the page left: the next change asks it again. */
    fun drop() {
        held = null
    }

    /** A household step left: the PIN goes with it only when no owner's view still needs it. */
    fun stepLeft() {
        if (!showing.value) drop()
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
