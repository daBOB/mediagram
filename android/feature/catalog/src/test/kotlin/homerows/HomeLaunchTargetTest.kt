package homerows

import catalog.shelvesOf
import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun aPickOverSomeoneChosenReopensThePickerThenEndsAfterTheChoice() {
        val pick = PickForHome()
        assertEquals(PickStep.Wait, pick.next(PickGate.Chosen), "not started")

        pick.start()
        assertEquals(PickStep.Wait, pick.next(PickGate.Loading), "the gate collects again from Loading")
        assertEquals(PickStep.Reopen, pick.next(PickGate.Chosen), "someone chosen and no picker yet: open it")
        assertEquals(PickStep.Wait, pick.next(PickGate.Loading))
        assertEquals(PickStep.Wait, pick.next(PickGate.Picking))
        assertEquals(PickStep.Done, pick.next(PickGate.Chosen), "someone chosen, or Stay as I am")
        assertEquals(PickStep.Wait, pick.next(PickGate.Chosen), "over once")
    }

    @Test
    fun aPickWithNobodyChosenEndsAfterTheFirstChoice() {
        val pick = PickForHome()
        pick.start()
        assertEquals(PickStep.Wait, pick.next(PickGate.Picking))
        assertEquals(PickStep.Done, pick.next(PickGate.Chosen))
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
