package system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.coreSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import settings.TmdbSettings
import java.util.Locale
import javax.inject.Inject

/**
 * Saves the TMDB key, and runs a fetch with it.
 *
 * The key itself passes through [saveKey] and [fetch] and is never kept in
 * [state] — only [FetchUiState.hasKey], which is all either screen this feeds
 * is allowed to know once a key is stored.
 *
 * A fetch is spent against whatever core [CoreProvider] currently holds; the
 * generated binding's own suspend function is what keeps this off the
 * caller's thread, the same way every other core call in this app already
 * relies on it — see [setup.Libraries.install] for the same shape.
 */
@HiltViewModel
class FetchViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val tmdbSettings: TmdbSettings,
) : ViewModel() {

    /**
     * The language to ask the provider in when the library itself does not
     * say what it was described in. Read once, here, because a fetch takes
     * minutes and a screen rotation in the middle of one must not change
     * what it asked for — and because the device is the only thing in this
     * app that knows which language its owner reads.
     */
    private val fallbackLanguage: String = Locale.getDefault().toLanguageTag()

    private val _state = MutableStateFlow(FetchUiState())
    val state: StateFlow<FetchUiState> = _state.asStateFlow()

    private val _postersArrived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Once each time a fetch — asked for or quiet — laid down new artwork, so
     * the shelves can be built again to show it. Not said when it fetched
     * none: descriptions are read on demand and need nothing rebuilt.
     */
    val postersArrived: SharedFlow<Unit> = _postersArrived.asSharedFlow()

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
     *
     * [quiet] is for the fetch nobody asked for — the one new media from
     * another device brings. It says nothing when it ends: a dialog after
     * every push would be a tally of work the viewer did not start, over
     * whatever they were doing. It also leaves any result still on screen
     * where it was. While it runs, the menu says so, as for any fetch.
     */
    fun fetch(quiet: Boolean = false) {
        val current = _state.value
        if (current.running || !current.hasKey) return

        viewModelScope.launch {
            _state.update { if (quiet) it.copy(running = true) else it.copy(running = true, report = null, error = null) }
            val key = tmdbSettings.read()
            if (key == null) {
                _state.update {
                    it.copy(running = false, hasKey = false, error = if (quiet) it.error else "No TMDB key is stored.")
                }
                return@launch
            }
            _state.value = try {
                val report = coreProvider.awaitCore().fetchMissing(key, fallbackLanguage)
                if (report.postersFetched > 0u) _postersArrived.tryEmit(Unit)
                if (quiet) _state.value.copy(running = false) else _state.value.copy(running = false, report = report)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (quiet) {
                    _state.value.copy(running = false)
                } else {
                    _state.value.copy(running = false, error = e.coreSentence() ?: "The fetch failed.")
                }
            }
        }
    }

    /** Clears the last result once the screen that showed it has. */
    fun dismissResult() {
        _state.update { it.copy(report = null, error = null) }
    }
}
