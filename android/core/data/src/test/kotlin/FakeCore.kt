package data

import kotlinx.coroutines.runBlocking
import settings.InMemoryPackageSettings
import settings.PackageSettings
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.SetSummary

class FakeCore(
    private val sets: List<SetSummary> = emptyList(),
    private val refreshResult: Long = 0L,
) : CoreClient {
    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = refreshResult
    override fun listSets(): List<SetSummary> = sets
    override fun posterPath(posterKey: String): String? = null
    override fun totalSize(setId: String): Long = 0
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
}

/**
 * A provider whose core is already there. These tests are about what the
 * repository does with a core, not about waiting for one; [CoreProviderTest]
 * covers the waiting.
 */
class ResolvedCoreProvider(private val core: CoreClient) : CoreProvider {
    override suspend fun awaitCore(): CoreClient = core
    override suspend fun coreOrNull(): CoreClient = core
    override suspend fun supply(apiId: Int, apiHash: String) = Unit
    override suspend fun forget() = Unit
}

/** A [SetSummary] with sensible defaults, so a test only names what it cares about. */
fun summary(
    setId: String = "set-1",
    kind: String = "movie",
    title: String? = "Title",
    show: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    episodeLast: Int? = null,
    year: Int? = null,
    duration: Int? = null,
    posterKey: String? = null,
    total: Long = 0L,
    partCount: Int = 1,
): SetSummary = SetSummary(
    setId = setId,
    kind = kind,
    title = title,
    show = show,
    chap = null,
    season = season?.toUInt(),
    episodeFirst = episodeFirst?.toUInt(),
    episodeLast = episodeLast?.toUInt(),
    year = year?.toUInt(),
    duration = duration?.toUInt(),
    posterKey = posterKey,
    total = total.toULong(),
    partCount = partCount.toUInt(),
)

fun settingsWithCredentials(): PackageSettings = InMemoryPackageSettings().apply {
    runBlocking { write("https://example.com/latest.json", "key-material") }
}
