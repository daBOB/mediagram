package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.HouseholdsRepository
import data.WatchStateRepository
import data.WatchSync
import data.orDefault
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.CreateHousehold
import model.DeleteHousehold
import model.HouseholdWords
import model.JoinHousehold
import model.MoveToHousehold
import model.ProfileOutcome
import model.ProfileRequest
import model.RenameHousehold
import model.SetHouseholdAdmin
import javax.inject.Inject

/**
 * The owner's Households part — the web's `settings-households.js`. It is
 * open for the owner watching here, or once [open] has the owner's PIN. That
 * PIN is asked once — at [open], or for the watching owner at the first
 * change — then held and sent with every change until [close]. A grown-up about to run a household with no PIN yet is
 * asked a first one, typed twice, before anything is sent. What a row offers
 * follows the household rule; the core decides all the same, and a refusal
 * is said in [HouseholdsOwnerUiState.Open.notice].
 */
@HiltViewModel
class HouseholdsOwnerViewModel
    @Inject
    constructor(
        private val repository: WatchStateRepository,
        private val households: HouseholdsRepository,
        private val sync: WatchSync,
    ) : ViewModel() {
        /** [visit] counts [close]s, so an answer to a change sent before one stays with its own visit. */
        private data class Step(val open: Boolean = false, val notice: String? = null, val answers: Int = 0, val sending: Boolean = false, val visit: Int = 0)

        private val step = MutableStateFlow(Step())
        private var heldPin: String? = null
        private val ask = PinAsk(viewModelScope)
        private val stillHere = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

        /** The owner's PIN, or a new admin's first one, being asked for. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        /** After [useHere]: whether the profile chosen here is still here; false means the picker is shown again. */
        val joined: SharedFlow<Boolean> = stillHere.asSharedFlow()

        val state: StateFlow<HouseholdsOwnerUiState> =
            combine(step, households.view, repository.chosenProfileId) { current, view, chosenId ->
                when {
                    view.list.isEmpty() -> HouseholdsOwnerUiState.Loading
                    current.open || view.watchedByOwner(chosenId) -> view.ownersPart(current.notice, current.answers, current.sending)
                    else -> HouseholdsOwnerUiState.Closed(view.owner != null)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HouseholdsOwnerUiState.Loading)

        /** The Owner button: the owner's PIN opens the part. */
        fun open() {
            reread()
            ownersPin { step.update { it.copy(open = true) } }
        }

        /** Run by grown-up [adminId], who moves in with their kids, or by a new one called [adminName]: one of the two. */
        fun create(name: String, adminId: String?, adminName: String?) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            val fresh = adminName?.trim()?.takeIf { it.isNotEmpty() }
            val admin = adminId?.let(::grownUp)
            if ((fresh == null) == (admin == null)) return
            act(
                newPinFor = fresh ?: admin?.takeUnless { it.hasPin }?.name,
                // Both names can be taken; the household's is said first, as the core checks it first.
                named = admin != null || households.view.value.list.any { householdKey(it.name) == householdKey(clean) },
            ) { actor, pin, newPin -> CreateHousehold(actor, pin, clean, admin?.id, fresh, newPin) }
        }

        fun rename(id: String, name: String) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            act(named = true) { actor, pin, _ -> RenameHousehold(actor, pin, id, clean) }
        }

        /** One of the household's own grown-ups. */
        fun changeAdmin(id: String, adminId: String) {
            val admin = grownUp(adminId) ?: return
            act(newPinFor = admin.takeUnless { it.hasPin }?.name) { actor, pin, newPin -> SetHouseholdAdmin(actor, pin, id, adminId, newPin) }
        }

        /** A grown-up and their kids. Into a household nobody runs they run it, so need a PIN. */
        fun moveHere(id: String, profileId: String) {
            val mover = grownUp(profileId) ?: return
            val nobodyRuns = households.view.value.list.find { it.id == id }?.adminId == null
            act(newPinFor = mover.name.takeIf { nobodyRuns && !mover.hasPin }) { actor, pin, newPin -> MoveToHousehold(actor, pin, id, profileId, newPin) }
        }

        /** Only an empty household other than Home. */
        fun delete(id: String) = act { actor, pin, _ -> DeleteHousehold(actor, pin, id) }

        /** This device shows household [id] from now, opened by the owner's PIN. */
        fun useHere(id: String) = act { _, pin, _ -> JoinHousehold(id, pin) }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()

        /** Leaves the part and forgets the owner's PIN; the watching owner's part stays open and asks it again. */
        fun close() {
            ask.cancel()
            heldPin = null
            step.value = Step(visit = step.value.visit + 1)
        }

        override fun onCleared() { heldPin = null }

        private fun reread() { viewModelScope.launch { households.reloadQuietly() } }

        private fun grownUp(id: String) = households.view.value.everyone.find { it.id == id && !it.kids }

        private fun isOpen() = step.value.open || households.view.value.watchedByOwner(repository.chosenProfileId.value)

        /** [request] sent as the owner with the held PIN — asked first if none is held — and [newPinFor]'s first PIN where named. */
        private fun act(
            newPinFor: String? = null,
            named: Boolean = false,
            request: (actorId: String, pin: String, newPin: String?) -> ProfileRequest,
        ) {
            if (!isOpen()) return
            val pin = heldPin ?: return ownersPin { act(newPinFor, named, request) }
            val actorId = households.view.value.owner?.id ?: return
            val visit = step.value.visit
            step.update { it.copy(notice = null) }
            val send: suspend (String?) -> ProfileOutcome = { newPin -> sending(visit) { sent(request(actorId, pin, newPin)) } }
            if (newPinFor == null) {
                viewModelScope.launch { report(visit, orDefault(null, "household change") { send(null) }, named) }
                return
            }
            ask.ask(
                PinPrompt(newPinTitleFor(newPinFor), newPin = true),
                send = { send(it) },
                done = { report(visit, ProfileOutcome.Done, named) },
                refused = { report(visit, it, named) },
            )
        }

        /** [block] with [HouseholdsOwnerUiState.Open.sending] on, for its own visit only. */
        private suspend fun sending(visit: Int, block: suspend () -> ProfileOutcome): ProfileOutcome {
            fun flag(on: Boolean) = step.update { if (it.visit == visit) it.copy(sending = on) else it }
            flag(true)
            return try { block() } finally { flag(false) }
        }

        private fun ownersPin(then: () -> Unit) {
            val owner = households.view.value.owner ?: return
            ask.ask(
                PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false),
                send = { pin -> repository.manage(ProfileRequest.Unlock(owner.id, pin)) },
                done = { pin -> heldPin = pin; then() },
            )
        }

        /**
         * [request] sent, and the households read again once it took. A join is
         * this device's alone, so nothing is pushed; who is here is read too,
         * and a read that fails throws, so the join is said not to have gone through.
         */
        private suspend fun sent(request: ProfileRequest): ProfileOutcome =
            if (request is JoinHousehold) {
                repository.manage(request).also { if (it == ProfileOutcome.Done) stillHere.emit(households.rereadAfterJoin(repository) { stillHere.tryEmit(false) }) }
            } else {
                repository.manageAndShare(request, sync).also { if (it == ProfileOutcome.Done) households.reloadQuietly() }
            }

        /** How a change went. A wrong PIN means the one held is no longer the owner's, so the next change asks again. */
        private fun report(visit: Int, outcome: ProfileOutcome?, named: Boolean) {
            val current = step.value.takeIf { it.visit == visit && isOpen() } ?: return
            if (outcome == ProfileOutcome.WrongPin) heldPin = null
            val notice =
                when {
                    outcome == ProfileOutcome.Done -> null
                    outcome == ProfileOutcome.NameTaken && named -> HouseholdWords.HOUSEHOLD_NAME_TAKEN
                    else -> outcome.reason()
                }
            step.value = current.copy(notice = notice, answers = current.answers + 1)
        }
    }
