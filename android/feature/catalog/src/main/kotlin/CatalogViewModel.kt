package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.refreshSentence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uniffi.mediagram_core.ShowInfo
import javax.inject.Inject

/** Refreshes the catalog on request, then groups it into shelves for the screen to render. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    // What [state] is built from. A cold flow handed to stateIn runs once
    // per subscription and never again, which left a viewer with no way to
    // ask the channel a second time short of killing the app. The initial
    // zero is the one automatic load the screen has always done; every
    // later value is somebody pressing for it.
    private val reloads = MutableStateFlow(0)

    /** Re-reads the library from the channel and re-groups it. */
    fun reload() {
        reloads.update { it + 1 }
    }

    // flatMapLatest, not flatMapConcat: a second request made while the
    // first is still in flight should replace it rather than queue behind
    // it, because both would install the same snapshot.
    val state: StateFlow<CatalogUiState> = reloads
        .flatMapLatest {
            flow {
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
                        shelves.isNotEmpty() -> CatalogUiState.Ready(shelves, failure?.refreshSentence())
                        failure != null -> CatalogUiState.Failed(failure.refreshSentence())
                        else -> CatalogUiState.Empty
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

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
