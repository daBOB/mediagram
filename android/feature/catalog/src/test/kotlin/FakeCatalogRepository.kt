package catalog

import data.CatalogRepository
import model.Kind
import model.MediaSet
import uniffi.mediagram_core.TitleInfo

class FakeCatalogRepository(
    movies: Int = 0,
    episodes: Int = 0,
    tutorials: Int = 0,
    private val refreshFails: Boolean = false,
    /** A catalog already on this device, which a failed refresh must not take away. */
    private val onDisk: Boolean = true,
) : CatalogRepository {

    private val allSets: List<MediaSet> =
        (0 until movies).map { fakeSet(Kind.MOVIE, "movie-$it") } +
            (0 until episodes).map { fakeSet(Kind.EPISODE, "episode-$it") } +
            (0 until tutorials).map { fakeSet(Kind.TUTORIAL, "tutorial-$it") }

    /** Whether a fetch has laid artwork down: from then on every set has a poster. */
    var postersArrived: Boolean = false

    /** How many times the channel has been asked, which is what a reload has to move. */
    var refreshes: Int = 0
        private set

    override suspend fun refresh(): Result<Int> {
        refreshes += 1
        return if (refreshFails) {
            Result.failure(IllegalStateException("refresh failed"))
        } else {
            Result.success(allSets.size)
        }
    }

    override suspend fun sets(): List<MediaSet> = when {
        !onDisk -> emptyList()
        postersArrived -> allSets.map { it.copy(posterPath = "/artwork/${it.setId}.jpg") }
        else -> allSets
    }

    /** Nothing is what a library assembled without a TMDB key answers, which is the ordinary case here. */
    override suspend fun titleInfo(posterKey: String): TitleInfo? = null

    /** No artwork held for any key — the ordinary case for a library assembled without a TMDB key. */
    override suspend fun posterPath(posterKey: String): String? = null
}

private fun fakeSet(kind: Kind, id: String) = MediaSet(
    setId = id,
    kind = kind,
    title = id,
    show = null,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = null,
    episodeLast = null,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
)
