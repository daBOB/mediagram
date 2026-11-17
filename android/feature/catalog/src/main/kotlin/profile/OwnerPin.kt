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
 * whether the owner's view shows, which a wrong PIN leaves as it is.
 */
@ActivityRetainedScoped
class OwnerPin @Inject constructor() {
    var held: String? = null

    private val showing = MutableStateFlow(false)

    /** Whether the owner's view of every household shows, from [show] until the page is left. */
    val view: StateFlow<Boolean> = showing.asStateFlow()

    fun show() {
        showing.value = true
    }

    /** A wrong PIN: the next change asks it again. */
    fun drop() {
        held = null
    }

    /** The page left: the PIN goes, and the owner's view with it. */
    fun leave() {
        drop()
        showing.value = false
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
