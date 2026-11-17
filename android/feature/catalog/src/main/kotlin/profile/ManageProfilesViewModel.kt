package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.HouseholdsRepository
import data.WatchStateRepository
import data.WatchSync
import data.orDefault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import javax.inject.Inject

/**
 * Manage profiles — the web's `profile-manage.js`: say who you are, prove it
 * with your PIN, then act within your role. The PIN is held only while the
 * panel is open and sent with every change, since the core checks PIN and role
 * each time; [close] drops it, as both gates do once the library shows again or
 * the app is left. Only [open] opens this — entering a profile never hands a
 * child the controls. The owner's view of every household ([parts]) opens at
 * once for the owner watching, or after the owner's tile and PIN ([actAs]),
 * which is the page's one ([OwnerPin]). The core decides all the same: what is
 * offered by mistake is still refused there, and the refusal is shown.
 */
@HiltViewModel
class ManageProfilesViewModel
    @Inject
    constructor(
        private val repository: WatchStateRepository,
        private val households: HouseholdsRepository,
        private val sync: WatchSync,
        private val ownerPin: OwnerPin,
    ) : ViewModel() {
        private val step = MutableStateFlow<ManageStep>(ManageStep.Closed)
        private var heldPin: String? = null
        private val ask = PinAsk(viewModelScope)

        /** Everyone in the household — what Settings' Profiles lists before anyone says who they are. */
        val profiles: StateFlow<List<Profile>> = repository.profiles

        /** The PIN Manage is asking for: the actor's own, or a new one typed twice. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        val state: StateFlow<ManageUiState> =
            combine(step, repository.profiles, households.view) { current, profiles, install -> current.shown(profiles, install) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ManageUiState.Closed)

        /** Who's managing? — or, with [chosen] the owner watching here, the owner's view at once. */
        fun open(chosen: Profile? = null) {
            ask.cancel()
            heldPin = null
            go(if (chosen?.owner == true && !chosen.kids) ManageStep.Managing(chosen.id) else ManageStep.ChoosingActor())
        }

        /**
         * The actor's PIN, or — from before PINs existed — a first one typed twice, which becomes theirs.
         * The owner's tile asks the owner's PIN, from any household's device, and the page holds it; one
         * given anywhere on the page is not asked again. An owner here with no PIN yet chooses one first.
         */
        fun actAs(id: String) {
            val here = repository.profiles.value.find { it.id == id && !it.kids }
            val boss = households.view.value.owner?.takeIf { it.id == id && (it.hasPin || here == null) }
            if (boss != null) return ask.ownersPin(ownerPin, repository, boss) { go(ManageStep.Managing(id)) }
            val actor = here ?: return
            ask.ask(
                PinPrompt(pinTitleFor(actor), newPin = !actor.hasPin),
                send = { pin -> shared(proving(actor, pin)) },
                done = { pin ->
                    hold(id, pin)
                    go(ManageStep.Managing(id))
                },
                refused = { outcome ->
                    go(ManageStep.ChoosingActor(outcome.reason()))
                    // A first PIN refused is news from another device: read who is here again.
                    viewModelScope.launch { repository.rereadQuietly() }
                },
            )
        }

        /** [minutes] null clears the kid's daily limit; refused for whoever may not set its age limit. */
        fun setDailyLimit(
            kidId: String,
            minutes: Int?,
        ) = change { actor, pin -> ProfileRequest.SetDailyLimit(actor, pin, kidId, minutes) }

        /** The whole list of section keys hidden from [id]. */
        fun setHiddenSections(id: String, sections: List<String>) = change { actor, pin -> ProfileRequest.SetHiddenSections(actor, pin, id, sections) }

        fun setKidsAge(
            kidId: String,
            age: Int,
        ) = change { actor, pin -> ProfileRequest.SetKidsAge(actor, pin, kidId, age) }

        /** Asked first on screen with [removeQuestion]; a grown-up's kids go with it. */
        fun remove(id: String) = change { actor, pin -> ProfileRequest.Remove(actor, pin, id) }

        /** The actor's own PIN, or — for the admin — another grown-up's; for the owner, in any household. */
        fun changePin(id: String) {
            val actorId = (step.value as? ManageStep.Managing)?.actorId ?: return
            val target = (repository.profiles.value + households.view.value.everyone).find { it.id == id } ?: return
            val own = id == actorId
            newPin(if (own) YOUR_NEW_PIN else resetPinTitleFor(target.name), own) { actor, pin, newPin ->
                ProfileRequest.SetPin(actor, pin, id, newPin)
            }
        }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()

        /** Leaves Manage and forgets the PIN it held. */
        fun close() {
            ask.cancel()
            heldPin = null
            ownerPin.drop()
            go(ManageStep.Closed)
        }

        override fun onCleared() {
            heldPin = null
        }

        internal fun change(request: (actorId: String, pin: String) -> ProfileRequest) {
            val actorId = (step.value as? ManageStep.Managing)?.actorId ?: return
            withPin(actorId) { pin ->
                val managing = (step.value as? ManageStep.Managing)?.takeIf { it.actorId == actorId } ?: return@withPin
                go(managing.copy(notice = null))
                viewModelScope.launch { report(actorId, orDefault(null, "profile change") { shared(request(actorId, pin)) }) }
            }
        }

        internal fun newPin(
            title: String,
            own: Boolean,
            request: (actorId: String, pin: String, newPin: String) -> ProfileRequest,
        ) {
            val actorId = (step.value as? ManageStep.Managing)?.actorId ?: return
            withPin(actorId) { pin ->
                ask.ask(
                    PinPrompt(title, newPin = true),
                    send = { newPin -> shared(request(actorId, pin, newPin)) },
                    done = { newPin ->
                        // A changed own PIN is the one sent from now on, or the next change is refused.
                        if (own) hold(actorId, newPin)
                        report(actorId, ProfileOutcome.Done)
                    },
                    refused = { outcome -> report(actorId, outcome) },
                )
            }
        }

        /** Every step Manage takes: the owner's view shows ([OwnerPin.view]) exactly while it manages as the owner. */
        private fun go(next: ManageStep) {
            step.value = next
            ownerPin.showView(next is ManageStep.Managing && owner(next.actorId) != null)
        }

        /** [actorId] when they are the install's owner, whichever household this device shows. */
        private fun owner(actorId: String) = (repository.profiles.value + households.view.value.everyone).find { it.id == actorId && it.owner && !it.kids }

        /** [pin] sent as [actorId] from now: the owner's is the whole page's. */
        private fun hold(actorId: String, pin: String) {
            if (owner(actorId) != null) ownerPin.held = pin else heldPin = pin
        }

        /** [then] with the PIN sent as [actorId]: theirs from Who's managing?, or the owner's, asked now if none is held. */
        private fun withPin(actorId: String, then: (String) -> Unit) {
            val owner = owner(actorId)
            if (owner == null) heldPin?.let(then) else ask.ownersPin(ownerPin, repository, owner, then)
        }

        /** [request] sent and shared; one that took reads the households again, which the owner's panel draws from too. */
        private suspend fun shared(request: ProfileRequest) = repository.manageAndShare(request, sync).also { if (it == ProfileOutcome.Done) households.reloadQuietly() }

        /** How a change went ([answered]): a wrong PIN is dropped, being no longer theirs. */
        private fun report(actorId: String, outcome: ProfileOutcome?) {
            val managing = (step.value as? ManageStep.Managing)?.takeIf { it.actorId == actorId } ?: return
            if (outcome == ProfileOutcome.WrongPin) {
                heldPin = null
                ownerPin.drop()
            }
            go(managing.answered(outcome, ownersView = owner(actorId) != null))
        }
    }
