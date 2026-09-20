package catalog

import data.CatalogRepository
import model.Kind
import model.MediaSet

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

    override suspend fun refresh(): Result<Int> =
        if (refreshFails) {
            Result.failure(IllegalStateException("refresh failed"))
        } else {
            Result.success(allSets.size)
        }

    override suspend fun sets(): List<MediaSet> = if (onDisk) allSets else emptyList()
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
