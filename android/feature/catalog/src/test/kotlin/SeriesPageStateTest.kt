package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesPageStateTest {
    private fun ep(
        show: String,
        season: Int,
        episode: Int,
        genres: List<String> = emptyList(),
        collectionId: Long? = null,
        popularity: Double? = null,
    ): MediaSet =
        MediaSet(
            setId = "$show-s${season}e$episode", kind = Kind.EPISODE, title = "Ep $season.$episode", show = show,
            chapter = null, path = null, season = season, episodeFirst = episode, episodeLast = null,
            year = null, durationSecs = null, posterPath = null, totalBytes = 0,
            genres = genres, collectionId = collectionId, popularity = popularity,
        )

    private fun collection(name: String, items: List<MediaSet>): Entry.Collection =
        Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = items.size, chapters = 1, divisions = listOf(Division(name, season = 1, items = items, children = emptyList())),
        )

    @Test
    fun resumesFromTheRealPositionMostRecentlyTouched() {
        val show = collection("Show", listOf(ep("Show", 1, 1), ep("Show", 1, 2)))
        val watch = WatchSnapshot.Empty.copy(
            progress = listOf(Progress(setId = "Show-s1e2", at = 600.0, duration = 1200.0, updatedAt = 5)),
        )
        val pick = seriesResumeFor(show, watch)
        assertEquals("Show-s1e2", pick?.set?.setId)
        assertEquals(ResumeVerb.RESUME, pick?.verb)
    }

    @Test
    fun aFreshShowOffersItsFirstEpisode() {
        val show = collection("Show", listOf(ep("Show", 1, 1)))
        val pick = seriesResumeFor(show, WatchSnapshot.Empty)
        assertEquals("Show-s1e1", pick?.set?.setId)
        assertEquals(ResumeVerb.PLAY, pick?.verb)
    }

    @Test
    fun similarShowsRanksByFranchiseThenGenreThenPopularityAndDropsItself() {
        val current = collection("Dune", listOf(ep("Dune", 1, 1, genres = listOf("Sci-Fi"), collectionId = 7)))
        val sameFranchise = collection("Dune Part Two", listOf(ep("Dune Part Two", 1, 1, collectionId = 7)))
        val sharedGenre = collection("Arrival", listOf(ep("Arrival", 1, 1, genres = listOf("Sci-Fi"), popularity = 50.0)))
        val unrelated = collection("Romance", listOf(ep("Romance", 1, 1, genres = listOf("Romance"))))

        val picks = similarShows(current, listOf(current, sameFranchise, sharedGenre, unrelated), watched = { false })

        assertEquals(listOf("series/Dune Part Two", "series/Arrival"), picks.map(Entry.Collection::key))
    }

    @Test
    fun aShowSeenInFullRanksBehindOneNotYetSeen() {
        val current = collection("Dune", listOf(ep("Dune", 1, 1, genres = listOf("Sci-Fi"))))
        val seenWhole = collection("Seen", listOf(ep("Seen", 1, 1, genres = listOf("Sci-Fi"))))
        val notSeen = collection("Fresh", listOf(ep("Fresh", 1, 1, genres = listOf("Sci-Fi"))))

        val watchedIds = setOf("Seen-s1e1")
        val picks = similarShows(current, listOf(current, seenWhole, notSeen), watched = { it in watchedIds })

        assertEquals(listOf("series/Fresh", "series/Seen"), picks.map(Entry.Collection::key))
    }

    @Test
    fun nothingSharedMeansNoSimilarShows() {
        val current = collection("Dune", listOf(ep("Dune", 1, 1, genres = listOf("Sci-Fi"))))
        val unrelated = collection("Romance", listOf(ep("Romance", 1, 1, genres = listOf("Romance"))))
        assertTrue(similarShows(current, listOf(current, unrelated), watched = { false }).isEmpty())
    }

    @Test
    fun aCollectionWithNoPlayableEpisodeHasNoResumeOrSimilarIdentity() {
        val empty = collection("Empty", emptyList())
        assertNull(seriesResumeFor(empty, WatchSnapshot.Empty))
        assertTrue(similarShows(empty, listOf(empty), watched = { false }).isEmpty())
    }
}
