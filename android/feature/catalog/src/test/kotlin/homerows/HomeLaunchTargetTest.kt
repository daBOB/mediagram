package homerows

import catalog.shelvesOf
import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where the home screen's ask lands in the library, and when a pick made for it is over. */
class HomeLaunchTargetTest {
    private val shelves = shelvesOf(listOf(film("m1")))

    @Test
    fun resumeOpensTheTitleAndPlaysIt() = assertEquals(LaunchTarget("m1", play = true), HomeLaunch.Resume("m1").targetIn(shelves))

    @Test
    fun openOpensTheTitleOnly() = assertEquals(LaunchTarget("m1", play = false), HomeLaunch.Open("m1").targetIn(shelves))

    @Test
    fun aTitleTheShelvesDoNotHoldLandsNowhere() {
        assertNull(HomeLaunch.Resume("gone").targetIn(shelves))
        assertNull(HomeLaunch.Open("gone").targetIn(shelves))
    }

    @Test
    fun pickProfileIsNoTitle() = assertNull(HomeLaunch.PickProfile.targetIn(shelves))

    @Test
    fun aPickIsOverOnlyAfterThePickerShowed() {
        val pick = PickForHome()
        assertFalse(pick.over(picking = false), "not started")

        pick.start()
        assertFalse(pick.over(picking = false), "still the person from before the picker opened")
        assertFalse(pick.over(picking = true))
        assertTrue(pick.over(picking = false), "someone chosen, or Stay as I am")
        assertFalse(pick.over(picking = false), "over once")
    }
}

private fun film(id: String) =
    MediaSet(
        setId = id,
        kind = Kind.MOVIE,
        title = id,
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = 3_600,
        posterPath = null,
        totalBytes = 0,
    )
