package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import model.Kind
import model.MediaSet
import javax.inject.Inject

private val SHELF_ORDER = listOf(
    Kind.MOVIE to "Movies",
    Kind.EPISODE to "Series",
    Kind.TUTORIAL to "Tutorials",
)

/** Refreshes the catalog once, then groups it into shelves for the screen to render. */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    val state: StateFlow<CatalogUiState> = flow {
        emit(CatalogUiState.Loading)
        val failure = repository.refresh().exceptionOrNull()
        if (failure != null) {
            emit(CatalogUiState.Failed(failure.message ?: "Could not refresh the library"))
            return@flow
        }
        val shelves = groupIntoShelves(repository.sets())
        emit(if (shelves.isEmpty()) CatalogUiState.Empty else CatalogUiState.Ready(shelves))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)
}

/** An empty shelf is omitted, not rendered empty. */
private fun groupIntoShelves(sets: List<MediaSet>): List<Shelf> =
    SHELF_ORDER.mapNotNull { (kind, title) ->
        sets.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { Shelf(title, it) }
    }
