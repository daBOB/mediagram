package ui.tv.catalog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.PortraitRequestLog
import model.FranchiseInfo
import model.Person
import model.TitleCredits
import javax.inject.Inject

/**
 * The read-only lookups the television's own new pages need — a title's
 * cast, a person, a franchise's TMDB overview, a portrait fetched lazily —
 * that `catalog.CatalogViewModel` does not yet pass through. Kept here
 * rather than added to that shared ViewModel: this phase owns `ui-tv` only,
 * and `feature:catalog` is another phase's file to extend, so a plain
 * pass-through living beside the screens that call it is the one place this
 * phase can put it without reaching outside its own module. `CatalogRepository`
 * already carries every one of these methods, interface-defaulted for a
 * fake that does not know them yet — this only forwards.
 */
@HiltViewModel
internal class TvCatalogExtras
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val portraits: PortraitRequestLog,
    ) : ViewModel() {
        suspend fun titleCredits(key: String): TitleCredits = repository.titleCredits(key)

        suspend fun person(personId: Long): Person? = repository.person(personId)

        suspend fun franchises(): List<FranchiseInfo> = repository.franchises()

        fun shouldRequestPortrait(personId: Long): Boolean = portraits.shouldRequest(personId)

        suspend fun fetchPortrait(personId: Long): String? = repository.fetchPortrait(personId)
    }
