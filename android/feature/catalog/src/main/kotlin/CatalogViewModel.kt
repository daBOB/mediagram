package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.coreSentence
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import uniffi.mediagram_core.ShowInfo
import javax.inject.Inject

/** Refreshes the catalog once, then groups it into shelves for the screen to render. */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    val state: StateFlow<CatalogUiState> = flow {
        emit(CatalogUiState.Loading)
        // The refresh is tried first and judged last. A catalog is a file on
        // this device, and it goes on being a whole library when the channel
        // cannot be reached — on a train, or while whoever uploads is midway
        // through tidying the channel. Losing the library over a failed
        // round trip would be the one failure a viewer cannot work around.
        val failure = repository.refresh().exceptionOrNull()
        val shelves = shelvesOf(runCatching { repository.sets() }.getOrDefault(emptyList()))
        emit(
            when {
                shelves.isNotEmpty() -> CatalogUiState.Ready(shelves, failure?.sentence())
                failure != null -> CatalogUiState.Failed(failure.sentence())
                else -> CatalogUiState.Empty
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

    /**
     * What the index says about one title, for the screen that describes it
     * before playing it.
     *
     * Asked for on demand rather than carried in [state]: the shelves hold
     * a few hundred sets and a viewer opens one of them, so joining every
     * synopsis into the catalog would do a few hundred queries to render
     * one screen.
     */
    suspend fun showInfo(posterKey: String): ShowInfo? = repository.showInfo(posterKey)
}

/**
 * What the core says, which is written to be read: what is wrong with the
 * channel and what to run about it. The generated exception's own message
 * is the bindings' field name and a value.
 */
private fun Throwable.sentence(): String =
    coreSentence() ?: message ?: "Could not refresh the library"
