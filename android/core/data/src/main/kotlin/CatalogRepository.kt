package data

import kotlinx.coroutines.CancellationException
import model.Kind
import model.MediaSet
import settings.LibrarySettings
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleInfo

interface CatalogRepository {
    suspend fun refresh(): Result<Int>
    suspend fun sets(): List<MediaSet>

    /**
     * What the index records about the title a poster key names, or nothing.
     *
     * Nothing is ordinary rather than exceptional: a course has no provider
     * entry, and a library assembled without a TMDB key has no rows at all.
     */
    suspend fun titleInfo(posterKey: String): TitleInfo?

    /**
     * The local file for a poster key that has no [model.MediaSet] of its
     * own to carry it on — a season's artwork. `null` is ordinary: not
     * every season has art of its own, and a library assembled without a
     * TMDB key has none at all.
     */
    suspend fun posterPath(posterKey: String): String?
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
) : CatalogRepository {

    /**
     * Re-reads the newest index the chosen library's channel holds. The handle
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
     *
     * A cancellation is not a refusal and is rethrown rather than recorded.
     * `runCatching` catches every [Throwable], the network call it wraps is
     * genuinely cancellable, and the catalog's flow is dropped five seconds
     * after its last subscriber — so a viewer who leaves mid-refresh would
     * otherwise come back to a screen quoting the coroutine machinery's own
     * words at them. Rethrowing also leaves the cancelled caller cancelled,
     * which is what everything above here expects.
     */
    override suspend fun refresh(): Result<Int> = runCatching {
        val handle = settings.read() ?: error("No library has been chosen on this device")
        val core = coreProvider.awaitCore()
        val before = core.catalogFacts().publishedAt
        val sets = core.refreshLibrary(handle).toInt()
        val after = core.catalogFacts().publishedAt
        refreshes.record(if (after == before) RefreshOutcome.AlreadyCurrent else RefreshOutcome.Updated)
        sets
    }.onFailure {
        if (it is CancellationException) throw it
        refreshes.record(RefreshOutcome.Refused(it.refreshSentence()))
    }

    override suspend fun sets(): List<MediaSet> {
        val core = coreProvider.awaitCore()
        return core.listSets().map { toMediaSet(core, it) }
    }

    /** The core is awaited here rather than captured, as everywhere else. */
    override suspend fun titleInfo(posterKey: String): TitleInfo? =
        coreProvider.awaitCore().titleInfo(posterKey)

    override suspend fun posterPath(posterKey: String): String? {
        val core = coreProvider.awaitCore()
        return core.posterPath(posterKey)
    }

    /**
     * One index row, as a set the shelves can place.
     *
     * A kind this does not recognise is shelved with the films rather than
     * dropped. A viewer who notices something in the wrong place can act on
     * it; a title that silently vanishes looks like a failed upload, and the
     * library holds four kinds today against a parser that may learn a
     * fifth before this app is rebuilt.
     */
    private fun toMediaSet(core: CoreClient, summary: SetSummary): MediaSet {
        val kind = when (summary.kind) {
            "ep" -> Kind.EPISODE
            "tut" -> Kind.TUTORIAL
            "doc" -> Kind.DOCUMENT
            else -> Kind.MOVIE
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
            addedAt = summary.addedAt,
            fsk = summary.fsk,
        )
    }
}
