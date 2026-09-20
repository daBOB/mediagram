package setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.CoreStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import settings.PackageSettings
import javax.inject.Inject

/**
 * Drives the three first-run steps and decides, each time it is asked,
 * which one is outstanding.
 *
 * Nothing typed here is remembered across a failed check: a value that does
 * not pass [SetupInput] is never written, so the step it belongs to is
 * still outstanding on the next look, by the same rule that decides every
 * other step. That is what makes an interrupted setup resume rather than
 * restart — there is no progress marker to be stale.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val packageSettings: PackageSettings,
    private val coreStorage: CoreStorage,
) : ViewModel() {

    private val _state = MutableStateFlow<SetupUiState>(SetupUiState.Checking)
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        recheck()
    }

    /**
     * Re-derives the outstanding step. Called after signing in, which this
     * ViewModel does not itself run, and after anything else that could
     * have changed what is true on disk.
     */
    fun recheck() {
        viewModelScope.launch { _state.value = outstandingStep() }
    }

    fun submitApplication(apiId: String, apiHash: String) {
        viewModelScope.launch {
            val id = apiIdOrNull(apiId)
            val hash = apiHashOrNull(apiHash)
            when {
                id == null -> _state.value = SetupUiState.NeedsApplication(API_ID_ERROR)
                hash == null -> _state.value = SetupUiState.NeedsApplication(API_HASH_ERROR)
                else -> {
                    coreProvider.supply(id, hash)
                    _state.value = outstandingStep()
                }
            }
        }
    }

    fun submitLibrary(url: String, keyB64: String) {
        viewModelScope.launch {
            val address = packageUrlOrNull(url)
            val key = packageKeyOrNull(keyB64)
            when {
                address == null -> _state.value = SetupUiState.NeedsLibrary(PACKAGE_URL_ERROR)
                key == null -> _state.value = SetupUiState.NeedsLibrary(PACKAGE_KEY_ERROR)
                else -> {
                    packageSettings.write(address, key)
                    _state.value = outstandingStep()
                }
            }
        }
    }

    /**
     * Back to step one, and back for real. Three things go together,
     * because leaving any one of them behind leaves the device in a state
     * no first run can produce: the package address and key, the Telegram
     * application identity, and — through [CoreStorage] — the auth key file
     * that is the actual session plus the catalog decrypted under the old
     * package key.
     *
     * The order matters on a process that dies midway: credentials first,
     * files last, so an interruption leaves less behind rather than a
     * session with no identity to use it.
     */
    fun startOver() {
        viewModelScope.launch {
            _state.value = SetupUiState.Checking
            packageSettings.clear()
            coreProvider.forget()
            coreStorage.clear()
            _state.value = outstandingStep()
        }
    }

    private suspend fun outstandingStep(): SetupUiState {
        val core = coreProvider.coreOrNull() ?: return SetupUiState.NeedsApplication()
        // Asked of the core, not of a flag set when signing in finished: an
        // account signed out from another device has no auth key here any
        // more, and this is the only way to notice.
        if (!core.isAuthorized()) return SetupUiState.NeedsSignIn
        if (packageSettings.read() == null) return SetupUiState.NeedsLibrary()
        return SetupUiState.Ready
    }
}
