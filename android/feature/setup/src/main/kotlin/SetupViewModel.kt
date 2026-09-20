package setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.CoreStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import settings.PackageSettings
import javax.inject.Inject

private const val STORAGE_FAILED = "This device's secure storage could not be read. " +
    "Starting over clears it and asks for everything again."

private const val RESET_FAILED = "Signing this device out did not finish. " +
    "Some of what was stored may still be here; try starting over again."

/**
 * Drives the three first-run steps and decides, each time it is asked,
 * which one is outstanding.
 *
 * Nothing typed here is remembered across a failed check: a value that does
 * not pass [SetupInput] is never written, so the step it belongs to is
 * still outstanding on the next look, by the same rule that decides every
 * other step. That is what makes an interrupted setup resume rather than
 * restart — there is no progress marker to be stale.
 *
 * Every path is guarded. The questions run through keystore-backed storage
 * and the native core, both of which can refuse; an exception escaping into
 * [viewModelScope] would take the process with it and leave nothing on
 * screen to explain or undo it.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val packageSettings: PackageSettings,
    private val coreStorage: CoreStorage,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<SetupUiState>(SetupUiState.Checking)
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        recheck()
    }

    /**
     * Re-derives the outstanding step. Called after signing in, which this
     * ViewModel does not itself run, on every return to the foreground, and
     * after anything else that could have changed what is true on disk.
     *
     * An error already on screen survives a re-derivation that lands on the
     * same step: a person coming back to the app has not stopped needing to
     * know why the last thing they typed was refused.
     */
    fun recheck() {
        settle { outstandingStep().keepingTheErrorFrom(_state.value) }
    }

    fun submitApplication(apiId: String, apiHash: String) {
        val id = apiIdOrNull(apiId)
        val hash = apiHashOrNull(apiHash)
        when {
            id == null -> _state.value = SetupUiState.NeedsApplication(API_ID_ERROR)
            hash == null -> _state.value = SetupUiState.NeedsApplication(API_HASH_ERROR)
            else -> settle {
                coreProvider.supply(id, hash)
                outstandingStep()
            }
        }
    }

    fun submitLibrary(url: String, keyB64: String) {
        val address = packageUrlOrNull(url)
        val key = packageKeyOrNull(keyB64)
        when {
            address == null -> _state.value = SetupUiState.NeedsLibrary(PACKAGE_URL_ERROR)
            key == null -> _state.value = SetupUiState.NeedsLibrary(PACKAGE_KEY_ERROR)
            else -> settle {
                withContext(dispatcher) { packageSettings.write(address, key) }
                outstandingStep()
            }
        }
    }

    /**
     * Back to step one, and back for real. Three things go together,
     * because leaving any one of them behind leaves the device in a state
     * no first run can produce: the auth key file that is the actual
     * session plus the catalog decrypted under the old package key, the
     * package address and key, and the Telegram application identity.
     *
     * Files first, credentials last. A Telegram auth key binds to the
     * datacentre, not to the api id it was obtained under, so a session
     * left on disk with no identity beside it is inherited wholesale by the
     * next identity typed in. Interrupted the other way round the device is
     * merely signed in with no way to use it, and the next look sends it to
     * the sign-in step — recoverable, and nobody else's account.
     */
    fun startOver() {
        settle(onFailure = RESET_FAILED) {
            coreStorage.clear()
            withContext(dispatcher) { packageSettings.clear() }
            coreProvider.forget()
            outstandingStep()
        }
    }

    private fun settle(onFailure: String = STORAGE_FAILED, work: suspend () -> SetupUiState) {
        viewModelScope.launch {
            _state.value = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The message is this app's own, never the exception's: a
                // platform message can name a file, a key alias or a
                // provider, and none of that belongs on a setup screen.
                SetupUiState.Failed(onFailure)
            }
        }
    }

    private suspend fun outstandingStep(): SetupUiState {
        val core = coreProvider.coreOrNull() ?: return SetupUiState.NeedsApplication()
        // Asked of the core, not of a flag set when signing in finished: an
        // account signed out from another device has no auth key here any
        // more, and this is the only way to notice. Off the caller's thread
        // because answering it reads a file.
        if (!withContext(dispatcher) { core.isAuthorized() }) return SetupUiState.NeedsSignIn
        if (withContext(dispatcher) { packageSettings.read() } == null) return SetupUiState.NeedsLibrary()
        return SetupUiState.Ready
    }
}

/** Returns [previous] unchanged when it is the same step carrying a message. */
private fun SetupUiState.keepingTheErrorFrom(previous: SetupUiState): SetupUiState = when {
    this is SetupUiState.NeedsApplication && previous is SetupUiState.NeedsApplication -> previous
    this is SetupUiState.NeedsLibrary && previous is SetupUiState.NeedsLibrary -> previous
    else -> this
}
