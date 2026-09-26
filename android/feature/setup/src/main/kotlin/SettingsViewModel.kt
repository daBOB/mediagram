package setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.CoreStorage
import data.WatchStateRepository
import data.coreSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import settings.TelegramSettings
import javax.inject.Inject

private const val UNKNOWN = "—"
private const val IDENTITY_CHANGE_FAILED = "The application identity could not be changed. Try again."
private const val SIGN_OUT_FAILED = "Signing out did not finish. Try again, or start over."
private const val PROFILE_RELOAD_FAILED = "Application identity changed, but profiles could not be loaded. Try again."

/**
 * The Telegram half of Settings: who is signed in, to which library, on
 * which datacentre — and the three things a person can change about it.
 *
 * It composes what setup already decided rather than deciding again:
 * [Libraries] installs before it remembers, [CoreProvider.replace] keeps an
 * identity only once Telegram answers through it. Every path is guarded as
 * setup's are, with this app's own sentences and never an exception's.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val libraries: Libraries,
        private val coreStorage: CoreStorage,
        private val telegramSettings: TelegramSettings,
        private val dispatcher: CoroutineDispatcher,
        private val watchState: WatchStateRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        private val _completions = MutableStateFlow<List<SettingsCompletion>>(emptyList())
        val completions: StateFlow<List<SettingsCompletion>> = _completions.asStateFlow()
        private var completeApplicationChangeAfterReload = false

        /** Acknowledge only the handled action; newer completions stay pending. */
        fun acknowledgeCompletion(id: Long) {
            _completions.update { pending -> pending.filterNot { it.id == id } }
        }

        private fun completed(event: SettingsEvent) {
            val id = _state.value.completedActionId + 1
            _state.update { it.copy(completedActionId = id) }
            _completions.update { it + SettingsCompletion(id, event) }
        }

        init {
            refresh()
        }

        /**
         * Asks every row again, from scratch. Called each time Settings opens:
         * this outlives the screen, and rows read before a sign-out and a new
         * sign-in would name the previous account.
         */
        fun refresh() {
            if (_state.value.busy) return
            _state.update { it.copy(account = null, library = null, datacenter = null, connection = null, choices = null) }
            act { readRows() }
        }

        /** Clears what the last action said, so a form opened next does not show it as its own. */
        fun clearNotice() = _state.update { it.copy(notice = null) }

        private suspend fun readRows() {
            val core = coreProvider.awaitCore()
            // Asked once and read twice: one round trip says both who is signed
            // in and whether Telegram is answering at all.
            val account = optionalRow { core.account() }
            val title = libraries.chosen()?.let { handle -> titleOf(handle) }
            val apiId = withContext(dispatcher) { telegramSettings.read()?.apiId }
            val dc = withContext(dispatcher) { core.dcId() }
            _state.update {
                it.copy(
                    account =
                        account?.let { a -> listOfNotNull(a.name, a.username?.let { u -> "(@$u)" }).joinToString(" ") }
                            ?: UNKNOWN,
                    library = title ?: UNKNOWN,
                    datacenter = dc?.let { "DC $it" } ?: UNKNOWN,
                    connection = if (account != null) "Signed in; Telegram answered" else "Telegram did not answer",
                    apiId = apiId,
                )
            }
        }

        private suspend fun titleOf(handle: String): String? = optionalRow { libraries.list() }?.find { it.handle == handle }?.title

        /** Lists the account's libraries so another can be chosen. */
        fun listLibraries() = act { _state.update { it.copy(choices = libraries.list()) } }

        /** Reads this app's active sessions. Its own error, apart from [SettingsUiState.notice]. */
        fun loadSessions() {
            viewModelScope.launch {
                try {
                    val sessions = coreProvider.awaitCore().sessions()
                    _state.update { it.copy(sessions = sessions, sessionsError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _state.update { it.copy(sessionsError = e.coreSentence() ?: "Could not read sessions. Try again.") }
                }
            }
        }

        /** Ends session [id], then re-reads the list so a revoked row disappears from the server's own answer. */
        fun revokeSession(id: String) {
            viewModelScope.launch {
                try {
                    coreProvider.awaitCore().revokeSession(id)
                    loadSessions()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _state.update { it.copy(sessionsError = e.coreSentence() ?: "Could not end that session. Try again.") }
                }
            }
        }

        /**
         * Installs [handle]'s catalog and only then remembers it, as setup does;
         * the shelves are told to read it once it is in.
         */
        fun chooseLibrary(handle: String) =
            act {
                libraries.install(handle)
                _state.update { it.copy(choices = null, notice = null) }
                completed(SettingsEvent.LibraryChanged)
                readRows()
            }

        fun changeApplication(
            apiId: String,
            apiHash: String,
        ) {
            if (_state.value.busy || _state.value.profileReloadNeeded) return
            val id = apiIdOrNull(apiId)
            val hash = apiHashOrNull(apiHash)
            when {
                id == null -> {
                    _state.update { it.copy(notice = API_ID_ERROR) }
                }

                hash == null -> {
                    _state.update { it.copy(notice = API_HASH_ERROR) }
                }

                else -> {
                    act {
                        try {
                            coreProvider.replace(id, hash)
                        } catch (e: CancellationException) {
                            _state.update { it.copy(profileReloadNeeded = true) }
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            // Replacement also closes native resources and writes
                            // local credentials; none of these failures establishes
                            // that Telegram rejected the application identity.
                            // Even a refused replacement retired the old core. Restore
                            // its watch owner from the credentials still stored on disk.
                            _state.update { it.copy(profileReloadNeeded = true) }
                            try {
                                reconcileProfiles()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (
                                @Suppress("TooGenericExceptionCaught") recovery: Exception,
                            ) {
                                e.addSuppressed(recovery)
                            }
                            throw SettingsFailure(IDENTITY_CHANGE_FAILED, e)
                        }
                        completeApplicationChangeAfterReload = true
                        _state.update { it.copy(apiId = id, profileReloadNeeded = true) }
                        reconcileProfiles()
                    }
                }
            }
        }

        /** Restores the watch owner without attempting another credential replacement. */
        fun retryProfiles() {
            if (_state.value.busy || !_state.value.profileReloadNeeded) return
            act { reconcileProfiles() }
        }

        private suspend fun reconcileProfiles() {
            try {
                watchState.reload()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val sentence = if (completeApplicationChangeAfterReload) PROFILE_RELOAD_FAILED else "Could not load profiles. Try again."
                throw SettingsFailure(sentence, e)
            }
            _state.update { it.copy(profileReloadNeeded = false) }
            if (completeApplicationChangeAfterReload) {
                completeApplicationChangeAfterReload = false
                completed(SettingsEvent.ApplicationChanged)
            }
            // The identity and profile owner are committed; unavailable decorative
            // rows must not turn this completed change into a refusal.
            optionalRow { readRows() }
        }

        /**
         * Signs out, which is less than starting over: the application identity
         * and the TMDB key stay, because they belong to this device rather than
         * to the account. At Telegram first, so the login stops working
         * everywhere; then the catalog and the names minted for its channels,
         * and the chosen library — all of them the signed-out account's.
         */
        fun signOut() =
            act(onFailure = SIGN_OUT_FAILED) {
                coreProvider.awaitCore().signOut()
                coreProvider.resetAccount(coreStorage)
                libraries.forget()
                watchState.invalidate()
                completeApplicationChangeAfterReload = false
                _state.update { it.copy(profileReloadNeeded = false) }
                completed(SettingsEvent.SignedOut)
            }

        private fun act(
            onFailure: String? = null,
            work: suspend () -> Unit,
        ) {
            _state.update { it.copy(busy = true, notice = null) }
            viewModelScope.launch {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    val notice = onFailure ?: (e as? SettingsFailure)?.sentence ?: e.coreSentence() ?: "That did not work. Try again."
                    _state.update { it.copy(notice = notice) }
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

/** Unavailable rows have a display fallback; cancellation still stops the action. */
private suspend fun <T> optionalRow(read: suspend () -> T): T? =
    try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        null
    }

/** A failure this ViewModel has already put into words. */
private class SettingsFailure(
    val sentence: String,
    cause: Exception,
) : Exception(sentence, cause)
