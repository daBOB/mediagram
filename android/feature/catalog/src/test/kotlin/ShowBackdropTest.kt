package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A show's backdrop is its first episode's that has one — every episode carries the show's art, as with the poster. */
class ShowBackdropTest {
    @Test
    fun aShowTakesTheFirstBackdropAnyOfItsEpisodesCarries() {
        val shows = collections(listOf(episode("e1", backdrop = null), episode("e2", backdrop = "/art/e2.jpg")), CollectionKind.SHOW, "Unknown")
        assertEquals("/art/e2.jpg", shows.single().backdropPath)
    }

    @Test
    fun aShowWhoseEpisodesCarryNoneHasNone() {
        assertNull(collections(listOf(episode("e1", backdrop = null)), CollectionKind.SHOW, "Unknown").single().backdropPath)
    }

    private fun episode(
        id: String,
        backdrop: String?,
    ) = MediaSet(
        setId = id,
        kind = Kind.EPISODE,
        title = id,
        show = "Reacher",
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
        addedAt = 0,
        rawTitle = id,
        backdropPath = backdrop,
    )
}
