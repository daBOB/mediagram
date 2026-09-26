package catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A hundred and seventy lessons named "Definition" and "Interpretation" are
 * not browsable, only searchable — the same reason the web keeps its search
 * box a flat, ranked list rather than a shelf.
 *
 * A plain [MutableStateFlow] this class writes directly, rather than one
 * derived from the query through `flatMapLatest`: [open] needs to reset it
 * to [SearchUiState.Idle] the instant it is called, not once a downstream
 * operator gets around to reacting to a new query value — a screen
 * reopened after visiting something else reads this ViewModel's current
 * value before that reaction would ever arrive, and a derived flow has no
 * value to give it until then but the stale one already sitting there.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** The one ranking in flight, if any — replaced rather than queued behind, the same thing `debounce` did. */
    private var pending: Job? = null

    fun setQuery(text: String) {
        pending?.cancel()
        if (text.isBlank()) {
            _state.value = SearchUiState.Idle
            return
        }
        pending = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = search(text)
        }
    }

    /** Nothing typed, at once — what the field falls back to rather than waiting out the pause. */
    fun clear() {
        pending?.cancel()
        _state.value = SearchUiState.Idle
    }

    /**
     * Enters with [initialQuery] — typed before, restored after a rotation
     * or a killed process, or blank on a fresh open. Called once, on the
     * way in: this ViewModel is scoped above the screen, so a second visit
     * would otherwise go on showing whatever the first one found.
     *
     * Resets to [SearchUiState.Idle] before asking anything, synchronously
     * rather than through the same pause [setQuery] waits out for an
     * ordinary keystroke — a reopened screen must never show a previous,
     * unrelated visit's rows even for the one frame before a real answer
     * can arrive.
     */
    fun open(initialQuery: String) {
        pending?.cancel()
        _state.value = SearchUiState.Idle
        if (initialQuery.isNotBlank()) setQuery(initialQuery)
    }

    /**
     * Ranks [text] against the catalog. The join onto a [model.MediaSet]
     * happens above this — see [searchRowsOf] — so this only ever asks the
     * core, never rebuilds the catalog itself.
     *
     * Timed and logged here rather than left to the core's own `tracing`,
     * which has no subscriber wired up on Android and would otherwise reach
     * nowhere a device can be asked to show it. A cancellation is not a
     * failure and is rethrown rather than shown — the query it belonged to
     * has already been replaced by a newer one.
     */
    private suspend fun search(text: String): SearchUiState {
        val started = System.nanoTime()
        return try {
            val hits = repository.search(text)
            val people = repository.searchPeople(text)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            Log.d(TAG, "\"$text\" ranked ${hits.size} hits and ${people.size} people in ${elapsedMs}ms")
            SearchUiState.Ready(hits, people)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            SearchUiState.Failed(error.message ?: "Search failed")
        }
    }

    companion object {
        private const val TAG = "search"
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
