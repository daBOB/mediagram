package player

import data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Kind
import model.MediaSet
import playback.PreloadItem
import playback.SeriesPreloading

/**
 * Feeds [SeriesPreloading] the next two titles of the open episode's own
 * show — the same `nextInQueue` up-next itself walks, so what is taken
 * ahead is exactly what would play next. The rule is the web's
 * `playsNext` (`web/public/lib/playback/plays-next.js`), which is the
 * reference: only a series episode preloads, only the next two positions
 * (those that are episodes), and nothing when the run was picked by hand —
 * a list or the Kids wall. The preload fetches in the background from a
 * flood-limited account; widening it is its own decision. Split out of
 * [PlayerViewModel] to keep that file under the project's line guideline.
 */
class PlayerPreloadController(
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val seriesPreloader: SeriesPreloading,
) {
    /**
     * A title has opened; asks the preloader for what follows it, when it is
     * itself an episode played through its show rather than a [handPicked] run.
     */
    fun startTitle(setId: String, run: List<String>, handPicked: Boolean) {
        if (handPicked) return
        scope.launch {
            val opened = catalogRepository.mediaSet(setId) ?: return@launch
            if (opened.kind != Kind.EPISODE) return@launch
            val next = nextEpisodes(run, setId)
            // Nothing sent when there is nothing to take — the same no-op
            // the web's own `requestPreload` makes for the last episode of a
            // season; whatever an earlier open already queued is left to
            // finish on its own.
            if (next.isNotEmpty()) {
                seriesPreloader.want(next.map { PreloadItem(it.setId, it.title, it.totalBytes) }, opened.totalBytes)
            }
        }
    }

    /**
     * The next two positions of [run] after [afterId], keeping those that
     * resolve to an episode — a set this build does not recognise, or one
     * the catalog can no longer find, is left out rather than replaced by
     * the one after it, the same as the web's server drops it.
     */
    private suspend fun nextEpisodes(run: List<String>, afterId: String): List<MediaSet> {
        val first = nextInQueue(run, afterId) ?: return emptyList()
        return listOfNotNull(first, nextInQueue(run, first))
            .mapNotNull { id -> catalogRepository.mediaSet(id)?.takeIf { it.kind == Kind.EPISODE } }
    }
}
