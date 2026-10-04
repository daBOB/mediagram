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
import model.ProfileRequest
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Drives "Who's watching?" — [WatchStateRepository] is the source of truth
 * for who exists and who is chosen; this only decides which of that to show
 * and when. Ports the web's picker (`profile-picker.js`): a kid's tile opens
 * at once, a grown-up's asks its PIN; a device with no grown-up makes the
 * first, and one with no admin asks who runs the household. Adding and
 * removing live in Manage, behind a grown-up's PIN. The web has no rename
 * either, so neither does this.
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
                val notice: String? = null,
            ) : Mode

            data class Chosen(
                val profileId: String,
            ) : Mode
        }

        private val mode = MutableStateFlow<Mode>(Mode.Loading)
        private var operation = 0L
        private val ask = PinAsk(viewModelScope)

        /** The PIN the picker is asking for, or null. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        // Each profile-owner entry re-reads the repository, even if setup completed immediately.
        val state: StateFlow<ProfileUiState> =
            combine(mode, repository.profiles, repository.chosenProfileId) { current, profiles, chosenId ->
                when (current) {
                    Mode.Loading -> {
                        ProfileUiState.Loading
                    }

                    // Someone to stay as: a profile removed in Manage takes the offer with it.
                    is Mode.Picking -> {
                        ProfileUiState.Picking(profiles, current.canStay && chosenId != null, current.error, current.notice)
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

        /**
         * A tile pressed. A kid opens at once — leaving a grown-up for a kid is
         * free. A grown-up asks its PIN, or, from before PINs existed, a first
         * one typed twice. Only the way in is locked: a remembered choice at
         * start-up asks nothing, and the core does not check [choose] — this
         * stops a child's tap, not someone with adb.
         */
        fun pick(id: String) {
            val profile = repository.profiles.value.find { it.id == id } ?: return
            noticed(null)
            if (profile.kids) return choose(id)
            ask.ask(
                PinPrompt(pinTitleFor(profile), newPin = !profile.hasPin),
                send = { pin -> repository.manageAndShare(proving(profile, pin), sync) },
                done = { choose(id) },
                refused = ::refused,
            )
        }

        /** "Who runs this household?" answered: that grown-up's PIN — or a first one — makes it the admin. */
        fun claim(id: String) {
            val profile = repository.profiles.value.find { it.id == id && !it.kids } ?: return
            noticed(null)
            ask.ask(
                PinPrompt(pinTitleFor(profile), newPin = !profile.hasPin),
                send = { pin -> repository.manageAndShare(ProfileRequest.ClaimAdmin(id, pin), sync) },
                done = {},
                refused = ::refused,
            )
        }

        /**
         * The first grown-up on a device with none; it runs the household, and
         * its tile then opens like anyone's. A device that does this before its
         * first sync, in a household that already has an admin, loses the role
         * to the older claim when the two meet — the merge's rule, not a bug.
         */
        fun createFirst(name: String) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            noticed(null)
            ask.ask(
                PinPrompt(newPinTitleFor(clean), newPin = true),
                send = { pin -> repository.manageAndShare(ProfileRequest.CreateFirstAdmin(clean, pin), sync) },
                done = {},
                refused = ::refused,
            )
        }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()

        /** Enters without a PIN — a kid, or a grown-up [pick] has unlocked. Tiles call [pick]. */
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

        private fun failed(
            message: String,
            previousId: String?,
        ) {
            val canStay = (mode.value as? Mode.Picking)?.canStay ?: (mode.value is Mode.Chosen)
            // A choice may have committed before its snapshot read failed. Do not
            // offer Stay as an acknowledgement of a different, unread profile.
            mode.value = Mode.Picking(canStay && previousId == repository.chosenProfileId.value, message)
        }

        /** A refused new PIN ended its prompt: said above the tiles, over who is here now — a refusal is usually news from another device. */
        private fun refused(sentence: String) {
            noticed(sentence)
            viewModelScope.launch {
                try {
                    repository.reload()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    // The tiles stay as they were; the notice already says what went wrong.
                }
            }
        }

        private fun noticed(sentence: String?) {
            (mode.value as? Mode.Picking)?.let { mode.value = it.copy(notice = sentence) }
        }

        /** The bar action: shows the picker again, with a way to change nothing. */
        fun reopen() {
            ask.cancel()
            operation++
            mode.value = Mode.Picking(canStay = true)
        }

        /** "Stay as I am" — back to whoever was already chosen. */
        fun stay() {
            if ((mode.value as? Mode.Picking)?.canStay != true) return
            ask.cancel()
            operation++
            repository.chosenProfileId.value?.let { mode.value = Mode.Chosen(it) }
        }
    }
