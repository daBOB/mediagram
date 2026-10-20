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
import java.text.Normalizer
import javax.inject.Inject

/**
 * The owner's Households part — the web's `settings-households.js`. The
 * owner's PIN is asked once, at [open], or — with the owner watching,
 * [openAsOwner] — at the first change, then held and sent with every change
 * until [close]. A grown-up about to run a household with no PIN yet is
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
        private data class Step(val open: Boolean = false, val notice: String? = null, val answers: Int = 0)

        private val step = MutableStateFlow(Step())
        private var heldPin: String? = null
        private val ask = PinAsk(viewModelScope)
        private val stillHere = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

        /** The owner's PIN, or a new admin's first one, being asked for. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        /** After [useHere]: whether the profile chosen here is still here; false means the picker is shown again. */
        val joined: SharedFlow<Boolean> = stillHere.asSharedFlow()

        val state: StateFlow<HouseholdsOwnerUiState> =
            combine(step, households.view) { current, view ->
                if (current.open) view.ownersPart(current.notice, current.answers) else HouseholdsOwnerUiState.Closed(view.owner != null)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HouseholdsOwnerUiState.Closed())

        /** The Owner button: the owner's PIN opens the part. */
        fun open() {
            reread()
            ownersPin { step.value = Step(open = true) }
        }

        /** The owner is watching: the part shows at once, and their PIN is asked at the first change. */
        fun openAsOwner() {
            reread()
            if (!step.value.open) step.value = Step(open = true)
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
                named = admin != null || households.view.value.list.any { key(it.name) == key(clean) },
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

        /** Leaves the part and forgets the owner's PIN. */
        fun close() {
            ask.cancel()
            heldPin = null
            step.value = Step()
        }

        override fun onCleared() {
            heldPin = null
        }

        private fun reread() {
            viewModelScope.launch { households.reloadQuietly() }
        }

        private fun grownUp(id: String) = households.view.value.everyone.find { it.id == id && !it.kids }

        /** [request] sent as the owner with the held PIN — asked first if none is held — and [newPinFor]'s first PIN where named. */
        private fun act(
            newPinFor: String? = null,
            named: Boolean = false,
            request: (actorId: String, pin: String, newPin: String?) -> ProfileRequest,
        ) {
            if (!step.value.open) return
            val pin = heldPin ?: return ownersPin { act(newPinFor, named, request) }
            val actorId = households.view.value.owner?.id ?: return
            step.value = step.value.copy(notice = null)
            if (newPinFor == null) {
                viewModelScope.launch { report(orDefault(null, "household change") { sent(request(actorId, pin, null)) }, named) }
                return
            }
            ask.ask(
                PinPrompt(newPinTitleFor(newPinFor), newPin = true),
                send = { newPin -> sent(request(actorId, pin, newPin)) },
                done = { report(ProfileOutcome.Done, named) },
                refused = { report(it, named) },
            )
        }

        private fun ownersPin(then: () -> Unit) {
            val owner = households.view.value.owner ?: return
            ask.ask(
                PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false),
                send = { pin -> repository.manage(ProfileRequest.Unlock(owner.id, pin)) },
                done = { pin ->
                    heldPin = pin
                    then()
                },
            )
        }

        /**
         * [request] sent, and the households read again once it took. A join is
         * this device's alone, so nothing is pushed; who is here is read too,
         * and a read that fails throws, so the join is said not to have gone through.
         */
        private suspend fun sent(request: ProfileRequest): ProfileOutcome =
            if (request is JoinHousehold) {
                repository.manage(request).also { if (it == ProfileOutcome.Done) stillHere.emit(households.rereadAfterJoin(repository)) }
            } else {
                repository.manageAndShare(request, sync).also { if (it == ProfileOutcome.Done) households.reloadQuietly() }
            }

        /** How a change went. A wrong PIN means the one held is no longer the owner's, so the next change asks again. */
        private fun report(outcome: ProfileOutcome?, named: Boolean) {
            val current = step.value.takeIf { it.open } ?: return
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

/** A name as household names are compared: case, spacing and Unicode form aside. */
private fun key(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase()
