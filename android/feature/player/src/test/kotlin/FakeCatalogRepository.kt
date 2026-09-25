package player

import data.CatalogRepository
import kotlinx.coroutines.CompletableDeferred
import model.MediaSet
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.TitleInfo

/**
 * Answers [mediaSet] straight out of a fixed map — this module's tests care
 * about what [PlayerViewModel] does with a resolved (or unresolved) set,
 * never about the shelf-building [sets] does for `feature:catalog`.
 *
 * [gate], held while running, is what lets a test open a genuine window
 * where [mediaSet] is still resolving — a plain fake with no suspension of
 * its own would always finish before a test's next line could run, and the
 * race [PlayerChoicesController] guards against would never actually happen
 * inside one.
 */
class FakeCatalogRepository(
    private val byId: Map<String, MediaSet> = emptyMap(),
    private val gate: CompletableDeferred<Unit>? = null,
) : CatalogRepository {

    override suspend fun refresh(): Result<Int> = Result.success(byId.size)

    override suspend fun sets(): List<MediaSet> = byId.values.toList()

    override suspend fun search(query: String): List<SearchHit> = emptyList()

    override suspend fun titleInfo(posterKey: String): TitleInfo? = null

    override suspend fun posterPath(posterKey: String): String? = null

    override suspend fun mediaSet(setId: String): MediaSet? {
        gate?.await()
        return byId[setId]
    }
}
