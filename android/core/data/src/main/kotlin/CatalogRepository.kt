package data

import model.Kind
import model.MediaSet
import settings.PackageSettings
import uniffi.mediagram_core.SetSummary

interface CatalogRepository {
    suspend fun refresh(): Result<Int>
    suspend fun sets(): List<MediaSet>
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
    private val settings: PackageSettings,
) : CatalogRepository {

    override suspend fun refresh(): Result<Int> = runCatching {
        val credentials = settings.read() ?: error("No package credentials configured")
        coreProvider.awaitCore().refreshCatalog(credentials.url, credentials.keyB64).toInt()
    }

    override suspend fun sets(): List<MediaSet> {
        val core = coreProvider.awaitCore()
        return core.listSets().mapNotNull { toMediaSetOrNull(core, it) }
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
            season = summary.season?.toInt(),
            episodeFirst = summary.episodeFirst?.toInt(),
            episodeLast = summary.episodeLast?.toInt(),
            year = summary.year?.toInt(),
            durationSecs = summary.duration?.toInt(),
            posterPath = summary.posterKey?.let(core::posterPath),
            totalBytes = summary.total.toLong(),
        )
    }
}
