package system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogEnrichmentFetcher
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TMDB credential editing and fetch feedback; library update sequencing lives in core:data. */
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

        fun fetch(quiet: Boolean = false) = viewModelScope.launch { enrichment.fetch(quiet) }

        fun dismissResult() = enrichment.dismissResult()
    }
