package system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogEnrichmentFetcher
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TMDB credential editing; fetching is the library update's, in core:data. */
@HiltViewModel
class FetchViewModel
    @Inject
    constructor(
        private val enrichment: CatalogEnrichmentFetcher,
    ) : ViewModel() {
        val state = enrichment.state

        init {
            // Reads the key status at once, then again whenever the home cache server comes or goes.
            viewModelScope.launch { enrichment.followHomeCache() }
        }

        fun refreshKeyStatus() {
            viewModelScope.launch { enrichment.refreshKeyStatus() }
        }

        fun saveKey(key: String) {
            viewModelScope.launch { enrichment.saveKey(key) }
        }
    }
