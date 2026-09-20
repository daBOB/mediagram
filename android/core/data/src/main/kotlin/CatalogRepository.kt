package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Kind
import model.MediaSet
import settings.LibrarySettings
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.ShowInfo

interface CatalogRepository {
    suspend fun refresh(): Result<Int>
    suspend fun sets(): List<MediaSet>

    /**
     * What the index records about the title a poster key names, or nothing.
     *
     * Nothing is ordinary rather than exceptional: a course has no provider
     * entry, and a library assembled without a TMDB key has no rows at all.
     */
    suspend fun showInfo(posterKey: String): ShowInfo?
}

/**
 * Reads the catalog through [CoreClient], mapping its raw `kind` strings
 * onto [Kind]. An unrecognised kind is dropped rather than crashing the
 * catalog — a newer uploader may write a kind this client predates.
 *
 * The core is awaited per call rather than held: this repository outlives
 * a "start over", which discards the core and builds the next one from
 * whatever identity is typed in after it.
 */
class DefaultCatalogRepository(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val refreshes: RefreshLog,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CatalogRepository {

    /**
     * Re-reads the index pinned in the chosen library's channel. The handle
     * is read per call rather than held, for the same reason the core is:
     * a "start over" replaces both, and a repository that had captured
     * either would go on refreshing a library the person had given back.
     *
     * What it did is recorded on the way through. The installed snapshot's
     * push time is read either side of the call and compared: both reads
     * are local — a symlink's own name — against a network round trip
     * between them, so the second costs nothing worth avoiding. Nothing
     * installed before and a time after is the first run, and that is
     * [RefreshOutcome.Updated] rather than a mistake.
     */
    override suspend fun refresh(): Result<Int> = runCatching {
        val handle = settings.read() ?: error("No library has been chosen on this device")
        val core = coreProvider.awaitCore()
        val before = core.catalogFacts().publishedAt
        val sets = core.refreshLibrary(handle).toInt()
        val after = core.catalogFacts().publishedAt
        refreshes.record(if (after == before) RefreshOutcome.AlreadyCurrent else RefreshOutcome.Updated)
        sets
    }.onFailure { refreshes.record(RefreshOutcome.Refused(it.refreshSentence())) }

    override suspend fun sets(): List<MediaSet> {
        val core = coreProvider.awaitCore()
        return core.listSets().mapNotNull { toMediaSetOrNull(core, it) }
    }

    /**
     * The core is awaited here rather than captured, as everywhere else, and
     * the query itself runs on [dispatcher]: `showInfo` is not one of the
     * generated suspend bindings, so it reads the catalog database on
     * whichever thread calls it, and the caller is a composition on main.
     */
    override suspend fun showInfo(posterKey: String): ShowInfo? {
        val core = coreProvider.awaitCore()
        return withContext(dispatcher) { core.showInfo(posterKey) }
    }

    private fun toMediaSetOrNull(core: CoreClient, summary: SetSummary): MediaSet? {
        val kind = when (summary.kind) {
            "movie" -> Kind.MOVIE
            "ep" -> Kind.EPISODE
            "tut" -> Kind.TUTORIAL
            else -> return null
        }
        return MediaSet(
            setId = summary.setId,
            kind = kind,
            title = summary.title ?: summary.show ?: summary.setId,
            show = summary.show,
            chapter = summary.chap,
            path = summary.path,
            season = summary.season?.toInt(),
            episodeFirst = summary.episodeFirst?.toInt(),
            episodeLast = summary.episodeLast?.toInt(),
            year = summary.year?.toInt(),
            durationSecs = summary.duration?.toInt(),
            posterPath = summary.posterKey?.let(core::posterPath),
            totalBytes = summary.total.toLong(),
            container = summary.container,
            vcodec = summary.vcodec,
            acodec = summary.acodec,
            quality = summary.quality,
            hdr = summary.hdr,
            partCount = summary.partCount.toInt(),
            posterKey = summary.posterKey,
        )
    }
}
