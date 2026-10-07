package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val sync: WatchSync,
    ) : ViewModel() {
        private sealed interface Step {
            data object Closed : Step

            data class ChoosingActor(
                val notice: String? = null,
            ) : Step

            data class Managing(
                val actorId: String,
                val notice: String? = null,
            ) : Step
        }

        private val step = MutableStateFlow<Step>(Step.Closed)
        private var heldPin: String? = null
        private val ask = PinAsk(viewModelScope)

        /** The PIN Manage is asking for: the actor's own, or a new one typed twice. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        val state: StateFlow<ManageUiState> =
            combine(step, repository.profiles) { current, profiles -> current.shown(profiles) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ManageUiState.Closed)

        fun open() {
            close()
            step.value = Step.ChoosingActor()
        }

        /** The actor's PIN, or — from before PINs existed — a first one typed twice, which becomes theirs. */
        fun actAs(id: String) {
            val actor = repository.profiles.value.find { it.id == id && !it.kids } ?: return
            ask.ask(
                PinPrompt(pinTitleFor(actor), newPin = !actor.hasPin),
                send = { pin -> repository.manageAndShare(proving(actor, pin), sync) },
                done = { pin ->
                    heldPin = pin
                    step.value = Step.Managing(id)
                },
                refused = { outcome ->
                    step.value = Step.ChoosingActor(outcome.reason())
                    // A first PIN refused is news from another device: read who is here again.
                    viewModelScope.launch { repository.rereadQuietly() }
                },
            )
        }

        /** [name] trimmed; a blank one is not sent, as the web's form sends none. */
        fun addKid(
            name: String,
            age: Int = NEW_KID_LIMIT,
        ) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            change { actor, pin -> ProfileRequest.CreateKid(actor, pin, clean, age) }
        }

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

        /** The actor's own PIN, or — for the admin — another grown-up's. */
        fun changePin(id: String) {
            val actorId = (step.value as? Step.Managing)?.actorId ?: return
            val target = repository.profiles.value.find { it.id == id } ?: return
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
            step.value = Step.Closed
        }

        override fun onCleared() {
            heldPin = null
        }

        private fun change(request: (actorId: String, pin: String) -> ProfileRequest) {
            val actorId = (step.value as? Step.Managing)?.actorId ?: return
            val pin = heldPin ?: return
            viewModelScope.launch {
                report(actorId, orDefault(null, "profile change") { repository.manageAndShare(request(actorId, pin), sync) })
            }
        }

        private fun newPin(
            title: String,
            own: Boolean,
            request: (actorId: String, pin: String, newPin: String) -> ProfileRequest,
        ) {
            val actorId = (step.value as? Step.Managing)?.actorId ?: return
            val pin = heldPin ?: return
            ask.ask(
                PinPrompt(title, newPin = true),
                send = { newPin -> repository.manageAndShare(request(actorId, pin, newPin), sync) },
                done = { newPin ->
                    // A changed own PIN is the one this panel sends from now on, or the next change is refused.
                    if (own) heldPin = newPin
                    report(actorId, ProfileOutcome.Done)
                },
                refused = { outcome -> report(actorId, outcome) },
            )
        }

        /**
         * How a change went — the web's `report`. Every change sends the PIN
         * held here, so a wrong one means it is no longer theirs: ask who they
         * are again rather than spend that profile's tries.
         */
        private fun report(
            actorId: String,
            outcome: ProfileOutcome?,
        ) {
            if ((step.value as? Step.Managing)?.actorId != actorId) return
            step.value =
                when (outcome) {
                    ProfileOutcome.Done -> Step.Managing(actorId)
                    ProfileOutcome.WrongPin -> {
                        heldPin = null
                        Step.ChoosingActor(PIN_NO_LONGER_VALID)
                    }
                    else -> Step.Managing(actorId, outcome.reason())
                }
        }

        private fun Step.shown(profiles: List<Profile>): ManageUiState =
            when (this) {
                Step.Closed -> ManageUiState.Closed
                is Step.ChoosingActor -> ManageUiState.ChoosingActor(profiles.filterNot { it.kids }, notice)
                is Step.Managing -> manageable(profiles, actorId, notice)
            }
    }
