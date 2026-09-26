package catalog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.PortraitRequestLog
import model.FranchiseInfo
import model.Person
import javax.inject.Inject

/**
 * The phone and TV department/person/franchise screens' own read surface —
 * a person by id, every franchise's TMDB overview, and a lazy portrait fetch
 * — over the same [CatalogRepository] [CatalogViewModel] already holds.
 *
 * A separate ViewModel rather than new methods on [CatalogViewModel], the
 * same split [SearchViewModel] already keeps from it: none of these three
 * calls touch the shelves or watch state [CatalogViewModel] owns, and a
 * screen that only ever opens a person or a franchise page has no reason to
 * recompose with every progress update that flows through the bigger one.
 */
@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val portraits: PortraitRequestLog,
    ) : ViewModel() {
        suspend fun person(personId: Long): Person? = repository.person(personId)

        suspend fun franchiseOverviews(): List<FranchiseInfo> = repository.franchises()

        suspend fun fetchPortrait(personId: Long): String? = repository.fetchPortrait(personId)

        fun shouldRequestPortrait(personId: Long): Boolean = portraits.shouldRequest(personId)
    }
