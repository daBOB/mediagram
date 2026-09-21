package system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.coreSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import settings.TmdbSettings
import javax.inject.Inject

private const val LANGUAGE = "en-US"

/**
 * Saves the TMDB key, and runs a fetch with it.
 *
 * The key itself passes through [saveKey] and [fetch] and is never kept in
 * [state] — only [PostersUiState.hasKey], which is all either screen this
 * feeds is allowed to know once a key is stored.
 *
 * A fetch is spent against whatever core [CoreProvider] currently holds;
 * the generated binding's own suspend function is what keeps this off the
 * caller's thread, the same way every other core call in this app already
 * relies on it — see [setup.Libraries.install] for the same shape.
 */
@HiltViewModel
class PostersViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val tmdbSettings: TmdbSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(PostersUiState())
    val state: StateFlow<PostersUiState> = _state.asStateFlow()

    init {
        refreshKeyStatus()
    }

    /** Re-reads whether a key is stored. Called on construction, and again once the key screen saves or clears one. */
    fun refreshKeyStatus() {
        viewModelScope.launch {
            val hasKey = tmdbSettings.read() != null
            _state.update { it.copy(hasKey = hasKey) }
        }
    }

    fun saveKey(key: String) {
        viewModelScope.launch {
            tmdbSettings.write(key)
            refreshKeyStatus()
        }
    }

    /**
     * Starts a fetch. A no-op while one is already running or no key is
     * stored — the menu that offers this action is disabled in both cases,
     * so this is the second, cheaper guard behind that one, not the first.
     */
    fun fetch() {
        val current = _state.value
        if (current.running || !current.hasKey) return

        viewModelScope.launch {
            _state.update { it.copy(running = true, report = null, error = null) }
            val key = tmdbSettings.read()
            if (key == null) {
                _state.update { it.copy(running = false, hasKey = false, error = "No TMDB key is stored.") }
                return@launch
            }
            _state.value = try {
                val report = coreProvider.awaitCore().fetchPosters(key, LANGUAGE)
                _state.value.copy(running = false, report = report)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.value.copy(running = false, error = e.coreSentence() ?: "The fetch failed.")
            }
        }
    }

    /** Clears the last result once the screen that showed it has. */
    fun dismissResult() {
        _state.update { it.copy(report = null, error = null) }
    }
}
