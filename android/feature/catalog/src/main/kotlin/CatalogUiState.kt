package catalog

import model.MediaSet

/** What the catalog screen renders; the television surface renders the same states. */
sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Ready(val shelves: List<Shelf>) : CatalogUiState
    data object Empty : CatalogUiState
    data class Failed(val message: String) : CatalogUiState
}

/** One horizontally scrolling row of the library, grouped by kind. */
data class Shelf(val title: String, val items: List<MediaSet>)
