package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CancellationException
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
    data class Picking(
        val profiles: List<Profile>,
        val canStay: Boolean,
        val error: String? = null,
    ) : ProfileUiState

    data class Chosen(
        val profile: Profile,
    ) : ProfileUiState
}

/**
 * Drives "Who's watching?" — [WatchStateRepository] is the source of truth
 * for who exists and who is chosen; this only decides which of that to show
 * and when. Ports the web's picker (`profile-picker.js`) minus rename and
 * delete, which sync cannot express yet — written up in phase 09's parity
 * note.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val repository: WatchStateRepository,
        private val sync: WatchSync,
    ) : ViewModel() {
        private sealed interface Mode {
            data object Loading : Mode

            data class Picking(
                val canStay: Boolean,
                val error: String? = null,
            ) : Mode

            data class Chosen(
                val profileId: String,
            ) : Mode
        }

        private val mode = MutableStateFlow<Mode>(Mode.Loading)
        private var operation = 0L

        // Each profile-owner entry re-reads the repository, even if setup completed immediately.
        val state: StateFlow<ProfileUiState> =
            combine(mode, repository.profiles) { current, profiles ->
                when (current) {
                    Mode.Loading -> {
                        ProfileUiState.Loading
                    }

                    is Mode.Picking -> {
                        ProfileUiState.Picking(profiles, current.canStay, current.error)
                    }

                    is Mode.Chosen -> {
                        profiles
                            .find { it.id == current.profileId }
                            ?.let(ProfileUiState::Chosen)
                            // The chosen row has not reached [profiles] yet — right
                            // after this device's own chooseProfile() — not a real gap.
                            ?: ProfileUiState.Loading
                    }
                }
            }.onStart { settle() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                    ProfileUiState.Loading,
                )

        /** Re-derives what to show from scratch: this device's own choice first, a sync round only when it has none. */
        private suspend fun settle() {
            val started = ++operation
            val previousId = repository.chosenProfileId.value
            val canStay = (mode.value as? Mode.Picking)?.canStay ?: (mode.value is Mode.Chosen)
            mode.value = Mode.Loading
            try {
                repository.reload()
                if (operation != started) return
                val chosenId = repository.chosenProfileId.value
                if (chosenId != null) {
                    mode.value = Mode.Chosen(chosenId)
                    return
                }
                withTimeoutOrNull(5.seconds) { sync.awaitFirstRound() }
                if (operation != started) return
                mode.value = Mode.Picking(canStay = false)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                if (operation != started) return
                mode.value =
                    Mode.Picking(
                        canStay = canStay && previousId == repository.chosenProfileId.value,
                        error = "Could not load profiles. Please try again.",
                    )
            }
        }

        /** Retries the local read without making a profile choice on a failed read's behalf. */
        fun retry() {
            viewModelScope.launch { settle() }
        }

        fun choose(id: String) {
            val started = ++operation
            viewModelScope.launch {
                val previousId = repository.chosenProfileId.value
                val chosen =
                    try {
                        repository.chooseProfile(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        false
                    }
                if (operation != started) return@launch
                if (chosen) {
                    mode.value = Mode.Chosen(id)
                } else {
                    failed("Could not choose that profile. Please try again.", previousId)
                }
            }
        }

        fun add(name: String) {
            val started = ++operation
            viewModelScope.launch {
                val previousId = repository.chosenProfileId.value
                val created =
                    try {
                        repository.createProfile(name) != null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        false
                    }
                if (operation != started) return@launch
                if (created) {
                    (mode.value as? Mode.Picking)?.let { mode.value = it.copy(error = null) }
                } else {
                    failed("Could not create the profile. Please try again.", previousId)
                }
            }
        }

        private fun failed(
            message: String,
            previousId: String?,
        ) {
            val canStay = (mode.value as? Mode.Picking)?.canStay ?: (mode.value is Mode.Chosen)
            // A choice may have committed before its snapshot read failed. Do not
            // offer Stay as an acknowledgement of a different, unread profile.
            mode.value = Mode.Picking(canStay && previousId == repository.chosenProfileId.value, message)
        }

        /** The bar action: shows the picker again, with a way to change nothing. */
        fun reopen() {
            operation++
            mode.value = Mode.Picking(canStay = true)
        }

        /** "Stay as I am" — back to whoever was already chosen. */
        fun stay() {
            if ((mode.value as? Mode.Picking)?.canStay != true) return
            operation++
            repository.chosenProfileId.value?.let { mode.value = Mode.Chosen(it) }
        }
    }
