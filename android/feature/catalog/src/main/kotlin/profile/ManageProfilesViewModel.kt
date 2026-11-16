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
 * with your PIN, then act within your role. The PIN is held here only while
 * the panel is open and sent with every change, since the core checks PIN and
 * role each time; [close] drops it — as both gates do once the library shows
 * again or the app is left, since this outlives the screen. Nothing opens this but [open] — entering
 * a profile never hands a child the controls. What the panel offers follows
 * the household's rule, but the core decides: something offered by mistake
 * is still refused there, and the refusal is shown as it comes.
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

        fun open() {
            ask.cancel()
            heldPin = null
            step.value = ManageStep.ChoosingActor()
        }

        /** The actor's PIN, or — from before PINs existed — a first one typed twice, which becomes theirs. */
        fun actAs(id: String) {
            val actor = repository.profiles.value.find { it.id == id && !it.kids } ?: return
            // The owner's PIN given anywhere on the page is not asked again.
            ownerPin.held?.takeIf { actor.owner }?.let { held ->
                heldPin = held
                step.value = ManageStep.Managing(id)
                return
            }
            ask.ask(
                PinPrompt(pinTitleFor(actor), newPin = !actor.hasPin),
                send = { pin -> shared(proving(actor, pin)) },
                done = { pin ->
                    heldPin = pin
                    if (actor.owner) ownerPin.held = pin
                    step.value = ManageStep.Managing(id)
                },
                refused = { outcome ->
                    step.value = ManageStep.ChoosingActor(outcome.reason())
                    // A first PIN refused is news from another device: read who is here again.
                    viewModelScope.launch { repository.rereadQuietly() }
                },
            )
        }

        /** [name] trimmed; a blank one is not sent, as the web's form sends none. */
        fun addKid(
            name: String,
            age: Int = NEW_KID_LIMIT,
            dailyLimit: Int? = null,
        ) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            change { actor, pin -> ProfileRequest.CreateKid(actor, pin, clean, age, dailyLimit) }
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

        /** Admin only; the new grown-up's first PIN is typed twice before anything is sent. */
        fun addGrownUp(name: String) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            newPin(newPinTitleFor(clean), own = false) { actor, pin, newPin -> ProfileRequest.CreateGrownUp(actor, pin, clean, newPin) }
        }

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
            step.value = ManageStep.Closed
        }

        override fun onCleared() {
            heldPin = null
        }

        private fun change(request: (actorId: String, pin: String) -> ProfileRequest) {
            val managing = step.value as? ManageStep.Managing ?: return
            val pin = heldPin ?: return
            step.value = managing.copy(notice = null)
            viewModelScope.launch {
                report(managing.actorId, orDefault(null, "profile change") { shared(request(managing.actorId, pin)) })
            }
        }

        private fun newPin(
            title: String,
            own: Boolean,
            request: (actorId: String, pin: String, newPin: String) -> ProfileRequest,
        ) {
            val actorId = (step.value as? ManageStep.Managing)?.actorId ?: return
            val pin = heldPin ?: return
            ask.ask(
                PinPrompt(title, newPin = true),
                send = { newPin -> shared(request(actorId, pin, newPin)) },
                done = { newPin ->
                    // A changed own PIN is the one this panel sends from now on, or the next change is refused.
                    if (own) {
                        heldPin = newPin
                        if (repository.profiles.value.find { it.id == actorId }?.owner == true) ownerPin.held = newPin
                    }
                    report(actorId, ProfileOutcome.Done)
                },
                refused = { outcome -> report(actorId, outcome) },
            )
        }

        /** [request] sent and shared; one that took reads the households again, which the owner's panel draws from too. */
        private suspend fun shared(request: ProfileRequest) = repository.manageAndShare(request, sync).also { if (it == ProfileOutcome.Done) households.reloadQuietly() }

        /**
         * How a change went — the web's `report`. Every change sends the PIN
         * held here, so a wrong one means it is no longer theirs: ask who they
         * are again rather than spend that profile's tries. Each answer is
         * counted, so even a refusal already on show — a new try clears it
         * first ([change]) — is news to a control waiting on its answer.
         */
        private fun report(actorId: String, outcome: ProfileOutcome?) {
            val answers = (step.value as? ManageStep.Managing)?.takeIf { it.actorId == actorId }?.answers ?: return
            step.value =
                when (outcome) {
                    ProfileOutcome.Done -> ManageStep.Managing(actorId, answers = answers + 1)
                    ProfileOutcome.WrongPin -> {
                        heldPin = null
                        ownerPin.drop()
                        ManageStep.ChoosingActor(PIN_NO_LONGER_VALID)
                    }
                    else -> ManageStep.Managing(actorId, outcome.reason(), answers + 1)
                }
        }
    }
