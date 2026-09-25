package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [magazineHomeOf] wires the shelves and watch state to [homeEditorial] and
 * the merged resume strip — the Kotlin side of `web/public/app.js`'s
 * `viewHome`. The picks' own rules are [EditorialPicksTest]'s job; this is
 * only whether the right inputs reach them.
 */
class MagazineHomeTest {
    private fun film(
        title: String,
        at: Long = 1,
        rating: Double? = 9.0,
        popularity: Double? = 90.0,
    ): MediaSet =
        MediaSet(
            setId = title,
            kind = Kind.MOVIE,
            title = title,
            show = null,
            chapter = null,
            path = null,
            season = null,
            episodeFirst = null,
            episodeLast = null,
            year = null,
            durationSecs = null,
            posterPath = "p-$title",
            totalBytes = 0,
            addedAt = at,
            backdropPath = "b-$title",
            rating = rating,
            popularity = popularity,
        )

    private fun episode(
        show: String,
        number: Int,
        at: Long = 1,
        rating: Double? = 9.0,
        popularity: Double? = 90.0,
    ): MediaSet =
        MediaSet(
            setId = "$show-$number",
            kind = Kind.EPISODE,
            title = "Episode $number",
            show = show,
            chapter = null,
            path = null,
            season = 1,
            episodeFirst = number,
            episodeLast = number,
            year = null,
            durationSecs = null,
            posterPath = "p-$show",
            totalBytes = 0,
            addedAt = at,
            backdropPath = "b-$show",
            rating = rating,
            popularity = popularity,
        )

    @Test
    fun onlyFilmsReachTheUnpinnedEditorialPicks() {
        val film = film("Alien", rating = 5.0, popularity = 5.0)
        // A series episode outranks the film on both facts editorial picks
        // score by, but the web draws trending and staff from films only.
        val show = episode("Highly Rated Show", number = 1, rating = 9.9, popularity = 99.0)
        val shelves = shelvesOf(listOf(film, show))

        val home = magazineHomeOf(shelves, WatchSnapshot.Empty, editorsChoice = null, now = 1_000_000_000_000)

        val featured = home.editorial.features.map { it.set.setId }
        assertTrue("Alien" in featured, "the film is the only thing eligible to lead")
        assertTrue("Highly Rated Show-1" !in featured, "an episode does not outrank a film it is not compared against")
    }

    @Test
    fun aPinnedEpisodeStillLeadsEvenThoughItIsNotAFilm() {
        val film = film("Alien")
        val show = episode("Pinned Show", number = 1)
        val shelves = shelvesOf(listOf(film, show))

        val home = magazineHomeOf(shelves, WatchSnapshot.Empty, editorsChoice = "Pinned Show-1", now = 1_000_000_000_000)

        assertEquals(FeatureKind.EDITOR, home.editorial.features.first().kind)
        assertEquals("Pinned Show-1", home.editorial.features.first().set.setId)
    }

    @Test
    fun theResumeStripListsContinuesBeforeNextUp() {
        val underway = film("Underway", at = 1)
        val fresh = film("Fresh Start", at = 2)
        val shelves = shelvesOf(listOf(underway, fresh))
        val watch =
            WatchSnapshot(
                progress = listOf(Progress(setId = "Underway", at = 120.0, duration = 3_600.0, updatedAt = 500)),
                watched = emptyList(),
                watchlist = emptyList(),
                kids = emptyList(),
                collections = emptyList(),
            )

        val home = magazineHomeOf(shelves, watch, editorsChoice = null, now = 1_000_000_000_000)

        assertEquals(listOf("Underway"), home.resumeCards.map { it.set.setId })
    }

    @Test
    fun recentlyAddedIsNewestFilmsFirstAndFeedsThisMonthsOnRowExclusion() {
        val now = 1_000_000_000_000L
        // Six films fill "Recently added" (HOME_ROW_LIMIT); a seventh, still
        // within the thirty-day window, is what "This month" is for. An
        // eighth outside that window belongs to neither.
        val row = (1..6).map { film("Row $it", at = now - it) }
        val monthOnly = film("This month only", at = now - 10 * DAY_MS)
        val tooOld = film("Too old", at = now - 40 * DAY_MS)
        val shelves = shelvesOf(row + monthOnly + tooOld)

        val home = magazineHomeOf(shelves, WatchSnapshot.Empty, editorsChoice = null, now = now)

        assertEquals(row.map(MediaSet::setId), home.recentlyAdded.map(MediaSet::setId))
        assertEquals(listOf("This month only"), home.editorial.thisMonth.map(MediaSet::setId))
    }
}
