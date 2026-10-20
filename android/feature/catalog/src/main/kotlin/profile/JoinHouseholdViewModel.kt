package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.HouseholdsRepository
import data.WatchStateRepository
import data.orDefault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.Household
import model.Households
import model.JoinHousehold
import model.ProfileOutcome
import model.joinPinTitle
import javax.inject.Inject

/**
 * Join a household, as the web's `household-join.js` offers it, and the
 * This device row. Everything is empty and false while the households have
 * not been read.
 */
data class JoinUiState(
    /**
     * The households have been read. Until they are, the picker shows the
     * waiting line with Try again ([JoinHouseholdViewModel.reread]) where it
     * would offer "Create the first profile", which the core refuses on an
     * install that has grown-ups in another household.
     */
    val read: Boolean = false,
    /** The live households but this device's own, each one a PIN can open: its admin's, or the owner's. */
    val households: List<Household> = emptyList(),
    /** This device's household, which the This device row names. */
    val here: Household? = null,
    /** More than one live household: only then is the This device row shown, with Switch household. */
    val canSwitch: Boolean = false,
    /**
     * None joined, in a households read made after a sync round: the picker
     * shows only this, never "Create the first profile", which the core
     * refuses on an install with grown-ups. Before a round, or before the
     * households are read again after one, not having joined is only waiting.
     */
    val needsJoin: Boolean = false,
    /**
     * This device's household has no grown-up left while another has one:
     * this stands where "Create the first profile" would, as the web's
     * `joinElsewhere` does.
     */
    val joinsElsewhere: Boolean = false,
)

/**
 * Which household this device shows: a household chosen, then its admin's
 * PIN or the owner's. A refused PIN keeps the prompt open with the reason.
 * Once one joins, [joined] says whether the profile chosen here is still
 * here; false means the picker is shown again. The device's household
 * changes on screen only once who is here and the households have both been
 * read again; a read that fails says so in the prompt, which asks again.
 * Drawn by the picker's gate and by Switch household alike.
 */
@HiltViewModel
class JoinHouseholdViewModel
    @Inject
    constructor(
        private val repository: WatchStateRepository,
        private val households: HouseholdsRepository,
    ) : ViewModel() {
        private val ask = PinAsk(viewModelScope)
        private val stillHere = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

        /** The PIN being asked for, or null. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        /** After each join: whether the profile chosen here is still here. */
        val joined: SharedFlow<Boolean> = stillHere.asSharedFlow()

        val state: StateFlow<JoinUiState> =
            combine(households.view, repository.syncedOnce, ::joinState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, JoinUiState())

        /** Read the households again as the list is shown: one may have been added on another device since. */
        fun reread() {
            viewModelScope.launch { households.reloadQuietly() }
        }

        fun pick(id: String) {
            val household = state.value.households.find { it.id == id } ?: return
            val admin = households.view.value.everyone.find { it.id == household.adminId }
            var kept = false
            ask.ask(
                PinPrompt(joinPinTitle(admin), newPin = false),
                // Left behind, the switch took: the picker shows, with no prompt over it saying otherwise.
                send = { pin -> households.joinThenReread(repository, JoinHousehold(id, pin), { ask.cancel(); stillHere.tryEmit(false) }) { kept = it } },
                done = { stillHere.tryEmit(kept) },
            )
        }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()
    }

private fun joinState(
    view: Households,
    synced: Boolean,
): JoinUiState {
    // None joined in a read from before the round now heard says nothing of joining yet; the round's own re-read will.
    if (view.list.isEmpty() || (synced && !view.synced && view.joined == null)) return JoinUiState()
    val ownerOpens = view.owner?.hasPin == true
    val grownUps = view.everyone.filterNot { it.kids }
    return JoinUiState(
        read = true,
        households = view.list.filter { one -> one.id != view.joined && (ownerOpens || view.everyone.any { it.id == one.adminId && it.hasPin }) },
        here = view.here,
        canSwitch = view.list.size > 1,
        needsJoin = view.needsJoin(synced),
        joinsElsewhere = view.joined != null && grownUps.isNotEmpty() && grownUps.none { it.household == view.joined },
    )
}

/**
 * [join] sent and, once it took, who is here then the households read again,
 * [kept] told whether the profile chosen here still is. A read that fails
 * throws; once who is here was read, a profile chosen before the join and
 * left behind by it is first told to [leftBehind], so the picker shows whether
 * or not the households read. With nobody chosen, nobody was left behind: the
 * failure is said where the join was asked, which asks again.
 */
internal suspend fun HouseholdsRepository.joinThenReread(
    watchState: WatchStateRepository,
    join: JoinHousehold,
    leftBehind: () -> Unit,
    kept: suspend (Boolean) -> Unit,
): ProfileOutcome {
    val chosenBefore = watchState.chosenProfileId.value != null
    val outcome = watchState.manage(join)
    if (outcome != ProfileOutcome.Done) return outcome
    watchState.reload()
    val stays = watchState.chosenProfileId.value != null
    try {
        reload()
    } catch (e: Exception) {
        if (chosenBefore && !stays && e !is CancellationException) leftBehind()
        throw e
    }
    kept(stays)
    return outcome
}

/** The households read again; a read that fails keeps what is shown, as a change that took has nothing to undo. */
internal suspend fun HouseholdsRepository.reloadQuietly() = orDefault(Unit, "households") { reload() }
