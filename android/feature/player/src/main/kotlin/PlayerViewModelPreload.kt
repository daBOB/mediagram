package player

import data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Kind
import model.MediaSet
import playback.PreloadItem
import playback.SeriesPreloading

/**
 * Feeds [SeriesPreloading] the next two episodes of the open title's own
 * run — the same `nextInQueue` up-next itself walks, so what is taken
 * ahead is exactly what would play next; ported from the web's own
 * `preloadAfter`. Split out of [PlayerViewModel] to keep that file under
 * the project's line guideline.
 */
class PlayerPreloadController(
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val seriesPreloader: SeriesPreloading,
) {
    /** A title has opened; asks the preloader for what follows it, when it is itself an episode of a show. */
    fun startTitle(setId: String, run: List<String>) {
        scope.launch {
            val opened = catalogRepository.mediaSet(setId) ?: return@launch
            if (opened.kind != Kind.EPISODE) return@launch
            val next = nextEpisodes(run, setId, WANTED_COUNT)
            // Nothing sent when there is nothing to take — the same no-op
            // the web's own `preloadAfter` makes for the last episode of a
            // season; whatever an earlier open already queued is left to
            // finish on its own.
            if (next.isNotEmpty()) {
                seriesPreloader.want(next.map { PreloadItem(it.setId, it.title, it.totalBytes) }, opened.totalBytes)
            }
        }
    }

    /**
     * Walks [run] forward one id at a time from [afterId], keeping only
     * the ones that resolve to an episode — a set this build does not
     * recognise, or one the catalog can no longer find, is skipped rather
     * than counted or than stopping the walk. Bounded by the run's own
     * length so a corrupted, cyclic run cannot loop this forever.
     */
    private suspend fun nextEpisodes(run: List<String>, afterId: String, count: Int): List<MediaSet> {
        val found = mutableListOf<MediaSet>()
        var cursor = afterId
        var steps = 0
        while (found.size < count && steps < run.size) {
            val nextId = nextInQueue(run, cursor) ?: break
            catalogRepository.mediaSet(nextId)?.takeIf { it.kind == Kind.EPISODE }?.let(found::add)
            cursor = nextId
            steps++
        }
        return found
    }

    private companion object {
        const val WANTED_COUNT = 2
    }
}
