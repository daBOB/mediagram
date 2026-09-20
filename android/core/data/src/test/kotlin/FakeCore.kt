package data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import settings.InMemoryLibrarySettings
import settings.LibrarySettings
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.PosterReport
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.ShowInfo

class FakeCore(
    private val sets: List<SetSummary> = emptyList(),
    private val refreshResult: Long = 0L,
    private val libraries: List<LibraryChoice> = emptyList(),
) : CoreClient {

    /** Which handle the last refresh was asked for, or `null` if none was. */
    var refreshedHandle: String? = null
        private set

    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun listLibraries(): List<LibraryChoice> = libraries

    override suspend fun refreshLibrary(handle: String): Long {
        refreshedHandle = handle
        return refreshResult
    }

    override suspend fun refreshCatalog(url: String, keyB64: String): Long = refreshResult
    override fun listSets(): List<SetSummary> = sets
    override fun posterPath(posterKey: String): String? = null
    override fun showInfo(posterKey: String): ShowInfo? = null
    override fun totalSize(setId: String): Long = 0
    override fun catalogFacts(): CatalogFacts = CatalogFacts("channel", 0uL, 0uL, 0u)
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
    override suspend fun fetchPosters(tmdbKey: String, language: String): PosterReport =
        PosterReport(0u, 0u, 0u, 0u)

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
)

fun settingsWithAChosenLibrary(handle: String = "a1b2c3"): LibrarySettings =
    InMemoryLibrarySettings().apply { runBlocking { write(handle) } }
