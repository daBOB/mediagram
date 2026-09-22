package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import settings.InMemoryLibrarySettings
import settings.LibrarySettings
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary
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
) : CoreClient {

    /** Which handle the last refresh was asked for, or `null` if none was. */
    var refreshedHandle: String? = null
        private set

    private var readings = 0

    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun listLibraries(): List<LibraryChoice> = libraries

    override suspend fun refreshLibrary(handle: String): Long {
        refreshedHandle = handle
        if (refreshCancels) throw CancellationException("the flow that asked was dropped")
        refreshFails?.let { error(it) }
        return refreshResult
    }

    override suspend fun refreshCatalog(url: String, keyB64: String): Long = refreshResult
    override suspend fun listSets(): List<SetSummary> = sets
    override fun posterPath(posterKey: String): String? = posters[posterKey]
    override suspend fun titleInfo(posterKey: String): TitleInfo? = null
    override suspend fun totalSize(setId: String): Long = 0
    override suspend fun catalogFacts(): CatalogFacts =
        CatalogFacts("channel", 0uL, 0uL, 0u, publishedAt[minOf(readings++, publishedAt.lastIndex)])
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
    override suspend fun fetchMissing(tmdbKey: String, language: String): FetchReport =
        FetchReport(0u, 0u, 0u, 0u, 0u, 0u)

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
class ResolvedCoreProvider(private val client: CoreClient) : CoreProvider {
    override val core: StateFlow<CoreClient?> = MutableStateFlow(client)
    override suspend fun awaitCore(): CoreClient = client
    override suspend fun coreOrNull(): CoreClient = client
    override suspend fun supply(apiId: Int, apiHash: String) = Unit
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
)

fun settingsWithAChosenLibrary(handle: String = "a1b2c3"): LibrarySettings =
    InMemoryLibrarySettings().apply { runBlocking { write(handle) } }
