package setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.CoreStorage
import data.coreSentence
import uniffi.mediagram_core.CoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import settings.TelegramSettings
import javax.inject.Inject

private const val UNKNOWN = "—"
private const val REFUSED_IDENTITY = "Telegram did not accept that application id. " +
    "The previous one is still in use."
private const val UNREACHABLE_IDENTITY = "Telegram could not be reached to try that application id. " +
    "The previous one is still in use."
private const val SIGN_OUT_FAILED = "Signing out did not finish. Try again, or start over."

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
class SettingsViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val libraries: Libraries,
    private val coreStorage: CoreStorage,
    private val telegramSettings: TelegramSettings,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    /**
     * Asks every row again, from scratch. Called each time Settings opens:
     * this outlives the screen, and rows read before a sign-out and a new
     * sign-in would name the previous account.
     */
    fun refresh() {
        _state.value = SettingsUiState()
        act { readRows() }
    }

    /** Clears what the last action said, so a form opened next does not show it as its own. */
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private suspend fun readRows() {
        val core = coreProvider.awaitCore()
        // Asked once and read twice: one round trip says both who is signed
        // in and whether Telegram is answering at all.
        val account = runCatching { core.account() }.getOrNull()
        val title = libraries.chosen()?.let { handle -> titleOf(handle) }
        val apiId = withContext(dispatcher) { telegramSettings.read()?.apiId }
        val dc = withContext(dispatcher) { core.dcId() }
        _state.update {
            it.copy(
                account = account?.let { a -> listOfNotNull(a.name, a.username?.let { u -> "(@$u)" }).joinToString(" ") }
                    ?: UNKNOWN,
                library = title ?: UNKNOWN,
                datacenter = dc?.let { "DC $it" } ?: UNKNOWN,
                connection = if (account != null) "Signed in; Telegram answered" else "Telegram did not answer",
                apiId = apiId,
            )
        }
    }

    private suspend fun titleOf(handle: String): String? =
        runCatching { libraries.list() }.getOrNull()?.find { it.handle == handle }?.title

    /** Lists the account's libraries so another can be chosen. */
    fun listLibraries() = act { _state.update { it.copy(choices = libraries.list()) } }

    /**
     * Installs [handle]'s catalog and only then remembers it, as setup does;
     * the shelves are told to read it once it is in.
     */
    fun chooseLibrary(handle: String) = act {
        libraries.install(handle)
        _state.update { it.copy(choices = null, notice = null) }
        _events.tryEmit(SettingsEvent.LibraryChanged)
        readRows()
    }

    fun changeApplication(apiId: String, apiHash: String) {
        val id = apiIdOrNull(apiId)
        val hash = apiHashOrNull(apiHash)
        when {
            id == null -> _state.update { it.copy(notice = API_ID_ERROR) }
            hash == null -> _state.update { it.copy(notice = API_HASH_ERROR) }
            else -> act {
                try {
                    coreProvider.replace(id, hash)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // An offline phone is not a refused id; the two ask for
                    // different things next.
                    throw SettingsFailure(if (e is CoreException.Network) UNREACHABLE_IDENTITY else REFUSED_IDENTITY)
                }
                _events.tryEmit(SettingsEvent.ApplicationChanged)
                // Outside the refusal's catch: the new identity is already in
                // use here, and a row that fails to read says nothing about it.
                runCatching { readRows() }
            }
        }
    }

    /**
     * Signs out, which is less than starting over: the application identity
     * and the TMDB key stay, because they belong to this device rather than
     * to the account. At Telegram first, so the login stops working
     * everywhere; then the catalog and the names minted for its channels,
     * and the chosen library — all of them the signed-out account's.
     */
    fun signOut() = act(onFailure = SIGN_OUT_FAILED) {
        coreProvider.awaitCore().signOut()
        coreStorage.clear()
        libraries.forget()
        _events.tryEmit(SettingsEvent.SignedOut)
    }

    private fun act(onFailure: String? = null, work: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = null) }
            val notice = try {
                work()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                onFailure ?: (e as? SettingsFailure)?.sentence ?: e.coreSentence() ?: "That did not work. Try again."
            }
            _state.update { it.copy(busy = false, notice = notice ?: it.notice) }
        }
    }
}

/** A failure this ViewModel has already put into words. */
private class SettingsFailure(val sentence: String) : Exception(sentence)
