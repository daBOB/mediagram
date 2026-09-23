package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import model.Profile
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** What "Who's watching?" shows, and what it is doing while it decides. */
sealed interface ProfileUiState {
    /**
     * Deciding what to show: reading this device's own choice and, only
     * when it has none, giving a sync round up to five seconds to bring in
     * the names other devices already use.
     */
    data object Loading : ProfileUiState

    /**
     * Nobody chosen yet, or the bar action asked to change who is. [canStay]
     * offers "Stay as I am" — true only on a reopen, since a first run has
     * nobody yet to stay as.
     */
    data class Picking(val profiles: List<Profile>, val canStay: Boolean) : ProfileUiState

    data class Chosen(val profile: Profile) : ProfileUiState
}

/**
 * Drives "Who's watching?" — [WatchStateRepository] is the source of truth
 * for who exists and who is chosen; this only decides which of that to show
 * and when. Ports the web's picker (`profile-picker.js`) minus rename and
 * delete, which sync cannot express yet — written up in phase 09's parity
 * note.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: WatchStateRepository,
    private val sync: WatchSync,
) : ViewModel() {

    private sealed interface Mode {
        data object Loading : Mode
        data class Picking(val canStay: Boolean) : Mode
        data class Chosen(val profileId: String) : Mode
    }

    private val mode = MutableStateFlow<Mode>(Mode.Loading)

    // Restarted, via WhileSubscribed, every time this is subscribed to after
    // five seconds with nobody watching — the same self-healing shape
    // CatalogViewModel's own state uses. That is what makes a profile
    // signed out from under a start-over resolve again rather than going on
    // showing the account that owned it: the setup flow this app returns to
    // for that takes far longer than five seconds to get back through.
    val state: StateFlow<ProfileUiState> = combine(mode, repository.profiles) { current, profiles ->
        when (current) {
            Mode.Loading -> ProfileUiState.Loading
            is Mode.Picking -> ProfileUiState.Picking(profiles, current.canStay)
            is Mode.Chosen -> profiles.find { it.id == current.profileId }
                ?.let(ProfileUiState::Chosen)
                // The chosen row has not reached [profiles] yet — right
                // after this device's own choose() — not a real gap.
                ?: ProfileUiState.Loading
        }
    }
        .onStart { settle() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    /** Re-derives what to show from scratch: this device's own choice first, a sync round only when it has none. */
    private suspend fun settle() {
        mode.value = Mode.Loading
        repository.reload()
        val chosenId = repository.chosenProfileId.value
        if (chosenId != null) {
            mode.value = Mode.Chosen(chosenId)
            return
        }
        withTimeoutOrNull(5.seconds) { sync.awaitFirstRound() }
        mode.value = Mode.Picking(canStay = false)
    }

    fun choose(id: String) {
        viewModelScope.launch { if (repository.choose(id)) mode.value = Mode.Chosen(id) }
    }

    fun add(name: String) {
        viewModelScope.launch { repository.create(name) }
    }

    /** The bar action: shows the picker again, with a way to change nothing. */
    fun reopen() {
        mode.value = Mode.Picking(canStay = true)
    }

    /** "Stay as I am" — back to whoever was already chosen. */
    fun stay() {
        repository.chosenProfileId.value?.let { mode.value = Mode.Chosen(it) }
    }
}
