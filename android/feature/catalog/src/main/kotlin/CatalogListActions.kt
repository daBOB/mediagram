package catalog

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * The four writes the Collections tab and its list screen make — split out
 * of [CatalogViewModel] to keep that file under the project's line
 * guideline. Each is a fire-and-forget wrapper over [data.WatchStateRepository],
 * the same shape `ProfileViewModel.add` already uses: the write goes to the
 * core on [androidx.lifecycle.ViewModel.viewModelScope] and [CatalogViewModel.state]
 * picks up the result on its own, through the snapshot already combined
 * into it — a caller in Compose has nothing to await.
 */
fun CatalogViewModel.createList(name: String) {
    viewModelScope.launch { watchState.createList(name) }
}

fun CatalogViewModel.renameList(id: String, name: String) {
    viewModelScope.launch { watchState.renameList(id, name) }
}

fun CatalogViewModel.deleteList(id: String) {
    viewModelScope.launch { watchState.deleteList(id) }
}

fun CatalogViewModel.setInList(id: String, setId: String, included: Boolean) {
    viewModelScope.launch { watchState.setInList(id, setId, included) }
}
