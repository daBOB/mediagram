package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Credit
import model.FranchiseInfo
import model.Kind
import model.MediaSet
import model.Person
import model.PersonHit
import model.TitleCredits
import settings.LibrarySettings
import uniffi.mediagram_core.CreditRecord
import uniffi.mediagram_core.PeopleHitRecord
import uniffi.mediagram_core.PersonRecord
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleInfo

/**
 * Reads await the current core and propagate provider, storage, and core
 * failures. Nullable metadata represents ordinary absence, not a failed read.
 * Cancellation propagates from every operation, including [refresh].
 */
interface CatalogRepository {
    /** Refreshes the installed index, returning its set count or an operational failure; cancellation is thrown. */
    suspend fun refresh(): Result<Int>

    suspend fun sets(): List<MediaSet>

    /**
     * The catalog's sets matching every word of [query], best first — the
     * same ranking [CoreClient.search] runs. A thin pass-through rather than
     * a join onto [sets]: the caller already holds that list and joining it
     * here would be a second copy of the same lookup.
     */
    suspend fun search(query: String): List<SearchHit>

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

    /**
     * One set by id, wherever it sits in the catalog — the player's own way
     * to resolve what a saved id names. Not `feature:catalog`'s
     * `CatalogUiState.mediaSet`, which walks a shelf tree built for
     * rendering: a feature module reaching into another feature module for
     * a lookup would be backwards, so this asks the catalog directly.
     * `null` with nothing found, including a catalog not yet loaded.
     */
    suspend fun mediaSet(setId: String): MediaSet? = sets().find { it.setId == setId }

    /**
     * A title's cast and crew, or both empty for an index with no `credits`
     * table (v8 and older) — not an error. Defaulted so a fake repository
     * (`feature:player`'s among them) need not know this call exists.
     */
    suspend fun titleCredits(key: String): TitleCredits = TitleCredits.Empty

    /** One person and the titles they are credited on, or `null` when nobody by this id is. */
    suspend fun person(personId: Long): Person? = null

    /** Every film franchise the index names, alphabetically. */
    suspend fun franchises(): List<FranchiseInfo> = emptyList()

    /** People whose name matches every word of [query], most-credited first. */
    suspend fun searchPeople(query: String): List<PersonHit> = emptyList()

    /** Downloads a person's portrait and answers its file's path, or `null` — see [CoreClient.fetchPortrait]. */
    suspend fun fetchPortrait(personId: Long): String? = null
}

/**
 * Reads the catalog through [CoreClient], mapping its raw `kind` strings
 * onto [Kind]. An unrecognised kind remains visible with the films because
 * a newer uploader may write a kind this client predates.
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
    override suspend fun refresh(): Result<Int> =
        runCatching {
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

    // `posterPath` is a plain synchronous call — a disk check per set with
    // a poster key — and `CoreClient` makes no promise of its own about
    // which thread a suspend function resumes on. Off [dispatcher] rather
    // than left to run wherever the caller's scope happens to be (usually
    // main, for a ViewModel): a few hundred of those in a row is exactly
    // the kind of main-thread stall that drops the touch event landing on
    // it, not just a slow frame.
    override suspend fun sets(): List<MediaSet> {
        val core = coreProvider.awaitCore()
        return withContext(dispatcher) { core.listSets().map { toMediaSet(core, it) } }
    }

    override suspend fun search(query: String): List<SearchHit> = coreProvider.awaitCore().search(query)

    /** The core is awaited here rather than captured, as everywhere else. */
    override suspend fun titleInfo(posterKey: String): TitleInfo? = coreProvider.awaitCore().titleInfo(posterKey)

    override suspend fun posterPath(posterKey: String): String? {
        val core = coreProvider.awaitCore()
        return core.posterPath(posterKey)
    }

    /**
     * Finds the one row before mapping any of them, rather than calling
     * [sets] and searching the result — [toMediaSet] pays for a poster
     * lookup per set, and the player asking for one title at a time has no
     * use for the other few hundred. Off [dispatcher] for the same reason
     * [sets] is; timed at debug level so a slow lookup shows up in logcat
     * without a release build ever printing it.
     */
    override suspend fun mediaSet(setId: String): MediaSet? = withContext(dispatcher) {
        val startedAt = System.nanoTime()
        val core = coreProvider.awaitCore()
        val found = core.listSets().find { it.setId == setId }?.let { toMediaSet(core, it) }
        Log.d(TAG, "mediaSet($setId): ${(System.nanoTime() - startedAt) / 1_000_000}ms")
        found
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
    private fun toMediaSet(
        core: CoreClient,
        summary: SetSummary,
    ): MediaSet {
        val kind =
            when (summary.kind) {
                "ep" -> Kind.EPISODE
                "tut" -> Kind.TUTORIAL
                "doc" -> Kind.DOCUMENT
                else -> Kind.MOVIE
            }
        return MediaSet(
            setId = summary.setId,
            kind = kind,
            title = summary.title ?: summary.show ?: summary.setId,
            rawTitle = summary.title,
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
            genres = summary.genres,
            subtitleLanguages = summary.subtitles,
            hasSummary = summary.hasSummary,
            backdropPath = summary.backdropKey?.let(core::posterPath),
            tagline = summary.tagline,
            rating = summary.rating,
            popularity = summary.popularity,
            collectionId = summary.collectionId?.toLong(),
            collectionName = summary.collectionName,
            seriesType = summary.seriesType,
            showStatus = summary.showStatus,
        )
    }

    override suspend fun titleCredits(key: String): TitleCredits {
        val core = coreProvider.awaitCore()
        val record = core.titleCredits(key)
        return TitleCredits(record.cast.map { it.toCredit(core) }, record.crew.map { it.toCredit(core) })
    }

    override suspend fun person(personId: Long): Person? {
        val core = coreProvider.awaitCore()
        val record: PersonRecord = core.person(personId) ?: return null
        return Person(record.personId.toLong(), record.name, record.portraitKey?.let(core::posterPath), record.titleKeys)
    }

    override suspend fun franchises(): List<FranchiseInfo> =
        coreProvider.awaitCore().franchises().map { FranchiseInfo(it.id.toLong(), it.name, it.overview) }

    override suspend fun searchPeople(query: String): List<PersonHit> {
        val core = coreProvider.awaitCore()
        return core.searchPeople(query).map { it.toPersonHit(core) }
    }

    override suspend fun fetchPortrait(personId: Long): String? = coreProvider.awaitCore().fetchPortrait(personId)

    private fun CreditRecord.toCredit(core: CoreClient): Credit =
        Credit(personId.toLong(), name, role, portraitKey?.let(core::posterPath))

    private fun PeopleHitRecord.toPersonHit(core: CoreClient): PersonHit =
        PersonHit(personId.toLong(), name, portraitKey?.let(core::posterPath), titleKeys)

    private companion object {
        const val TAG = "catalog"
    }
}
