package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import settings.InMemoryLibrarySettings
import settings.LibrarySettings
import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.FranchiseRecord
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.PeopleHitRecord
import uniffi.mediagram_core.PersonRecord
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleCreditsRecord
import uniffi.mediagram_core.TitleInfo

class FakeCore(
    private val sets: List<SetSummary> = emptyList(),
    private val refreshResult: Long = 0L,
    private val libraries: List<LibraryChoice> = emptyList(),
    /**
     * What each reading of [catalogFacts] says the installed snapshot was
     * pushed at, in order. A refresh takes one reading either side of
     * itself, so two entries are a push that landed; the last entry stands
     * for every reading after it, so one entry is a library that never
     * changes.
     */
    private val publishedAt: List<Long?> = listOf(null),
    /** The sentence [refreshLibrary] raises with, or `null` when it succeeds. */
    private val refreshFails: String? = null,
    /** Whether [refreshLibrary] is cancelled rather than finishing or failing. */
    private val refreshCancels: Boolean = false,
    /** What [posterPath] answers for a key it holds; any other key answers nothing. */
    private val posters: Map<String, String> = emptyMap(),
    /**
     * What each wait in [nextLibraryEvent] answers, in order; a failure is
     * thrown. Past the end, a wait waits for ever, as a quiet channel does.
     */
    private val events: List<Result<LibraryEvent>> = emptyList(),
    /** What [account] answers, or throws: whether Telegram accepts this core's identity. */
    private val account: Result<AccountSummary> = Result.success(AccountSummary("A Viewer", "viewer")),
    /** What [search] answers, regardless of the query asked. */
    private val searchHits: List<SearchHit> = emptyList(),
    /** What [titleCredits] answers, regardless of the key asked. */
    private val creditsAnswer: TitleCreditsRecord = TitleCreditsRecord(cast = emptyList(), crew = emptyList()),
    /** What [person] answers, keyed by the id asked. */
    private val people: Map<Long, PersonRecord> = emptyMap(),
    /** What [franchises] answers. */
    private val franchiseRecords: List<FranchiseRecord> = emptyList(),
    /** What [searchPeople] answers, regardless of the query asked. */
    private val peopleHits: List<PeopleHitRecord> = emptyList(),
    /** What [fetchPortrait] answers, keyed by the id asked. */
    private val portraits: Map<Long, String> = emptyMap(),
) : CoreClient {

    override suspend fun titleCredits(key: String): TitleCreditsRecord = creditsAnswer

    override suspend fun person(personId: Long): PersonRecord? = people[personId]

    override suspend fun franchises(): List<FranchiseRecord> = franchiseRecords

    override suspend fun searchPeople(query: String): List<PeopleHitRecord> = peopleHits

    override suspend fun fetchPortrait(personId: Long): String? = portraits[personId]

    var searchedFor: String? = null
        private set

    override suspend fun search(query: String): List<SearchHit> {
        searchedFor = query
        return searchHits
    }

    override suspend fun account(): AccountSummary = account.getOrThrow()

    var signedOut: Boolean = false
        private set

    override suspend fun signOut() {
        signedOut = true
    }

    /** How many times [nextLibraryEvent] has been waited on, and for which handle last. */
    var eventCalls: Int = 0
        private set
    var eventHandle: String? = null
        private set

    override suspend fun nextLibraryEvent(
        handle: String,
        ownDevice: String,
    ): LibraryEvent {
        eventHandle = handle
        val next = events.getOrNull(eventCalls++) ?: awaitCancellation()
        return next.getOrThrow()
    }

    /** Which handle the last refresh was asked for, or `null` if none was. */
    var refreshedHandle: String? = null
        private set

    private var readings = 0

    override fun isAuthorized(): Boolean = true

    override suspend fun requestCode(phone: String): String = "token"

    override suspend fun signIn(
        token: String,
        code: String,
    ): AuthOutcome = AuthOutcome.DONE

    override suspend fun checkPassword(password: String) = Unit

    override suspend fun listLibraries(): List<LibraryChoice> = libraries

    override suspend fun refreshLibrary(handle: String): Long {
        refreshedHandle = handle
        if (refreshCancels) throw CancellationException("the flow that asked was dropped")
        refreshFails?.let { error(it) }
        return refreshResult
    }

    override suspend fun refreshCatalog(
        url: String,
        keyB64: String,
    ): Long = refreshResult

    override suspend fun listSets(): List<SetSummary> = sets

    /** Every key [posterPath] was asked for, in order — a test's way of seeing how many sets a lookup actually mapped. */
    val posterPathCalls: MutableList<String> = mutableListOf()

    override fun posterPath(posterKey: String): String? {
        posterPathCalls += posterKey
        return posters[posterKey]
    }
    override suspend fun titleInfo(posterKey: String): TitleInfo? = null

    override suspend fun totalSize(setId: String): Long = 0

    override suspend fun catalogFacts(): CatalogFacts =
        CatalogFacts("channel", 0uL, 0uL, 0u, publishedAt[minOf(readings++, publishedAt.lastIndex)])

    override suspend fun read(
        setId: String,
        offset: Long,
        len: Int,
    ): ByteArray = ByteArray(0)

    override suspend fun fetchMissing(
        tmdbKey: String,
        language: String,
        backdropWidth: Int,
    ): FetchReport = FetchReport(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)

    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
    }
}

/**
 * A provider whose core is already there. These tests are about what the
 * repository does with a core, not about waiting for one; [CoreProviderTest]
 * covers the waiting.
 */
class ResolvedCoreProvider(
    private val client: CoreClient,
) : CoreProvider {
    override suspend fun replace(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override val core: StateFlow<CoreClient?> = MutableStateFlow(client)

    override suspend fun awaitCore(): CoreClient = client

    override suspend fun coreOrNull(): CoreClient = client

    override suspend fun supply(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override suspend fun resetAccount(storage: data.CoreStorage) = error("this fixture does not reset accounts")

    override suspend fun forget() = Unit
}

/** A [SetSummary] with sensible defaults, so a test only names what it cares about. */
fun summary(
    setId: String = "set-1",
    kind: String = "movie",
    title: String? = "Title",
    show: String? = null,
    chap: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    episodeLast: Int? = null,
    year: Int? = null,
    container: String = "mp4",
    vcodec: String? = null,
    acodec: String? = null,
    quality: String? = null,
    hdr: String? = null,
    duration: Int? = null,
    posterKey: String? = null,
    total: Long = 0L,
    partCount: Int = 1,
    addedAt: Long = 0,
    fsk: String? = null,
    genres: List<String> = emptyList(),
    subtitles: List<String> = emptyList(),
    hasSummary: Boolean = false,
    backdropKey: String? = null,
    tagline: String? = null,
    rating: Double? = null,
    popularity: Double? = null,
    showStatus: String? = null,
    collectionId: Long? = null,
    collectionName: String? = null,
    seriesType: String? = null,
): SetSummary = SetSummary(
    setId = setId,
    kind = kind,
    title = title,
    show = show,
    chap = chap,
    path = path,
    season = season?.toUInt(),
    episodeFirst = episodeFirst?.toUInt(),
    episodeLast = episodeLast?.toUInt(),
    year = year?.toUInt(),
    container = container,
    vcodec = vcodec,
    acodec = acodec,
    quality = quality,
    hdr = hdr,
    duration = duration?.toUInt(),
    posterKey = posterKey,
    total = total.toULong(),
    partCount = partCount.toUInt(),
    addedAt = addedAt,
    fsk = fsk,
    genres = genres,
    subtitles = subtitles,
    hasSummary = hasSummary,
    backdropKey = backdropKey,
    tagline = tagline,
    rating = rating,
    popularity = popularity,
    showStatus = showStatus,
    collectionId = collectionId?.toULong(),
    collectionName = collectionName,
    seriesType = seriesType,
)

fun settingsWithAChosenLibrary(handle: String = "a1b2c3"): LibrarySettings =
    InMemoryLibrarySettings().apply { runBlocking { write(handle) } }
