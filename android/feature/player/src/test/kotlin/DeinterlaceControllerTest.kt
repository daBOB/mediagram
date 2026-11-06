package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whether deinterlacing is on for the open show, remembered per show by [DeinterlaceController] and applied to the player handle. */
class DeinterlaceControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "Roseanne")
    private val film = fakeMediaSet(setId = "film1")
    private val hdrShow = fakeMediaSet(setId = "ep9", posterKey = "show-h", show = "Planet").copy(hdr = "HDR10")

    @Test
    fun openingAShowWithItRememberedSwitchesItOnAndTellsThePlayer() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("deinterlace" to "on")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)

        vm.open(show.setId)
        advanceUntilIdle()

        assertTrue(vm.choices.value.deinterlace)
        assertEquals(true, handle.deinterlaceCalls.last())
    }

    @Test
    fun aShowWithNothingRememberedIsOffAndNeverTurnsTheFilterOn() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)))

        vm.open(show.setId)
        advanceUntilIdle()

        assertFalse(vm.choices.value.deinterlace)
        assertFalse(handle.deinterlaceCalls.any { it })
    }

    @Test
    fun choosingItRemembersOnAndChoosingItOffForgetsTheRow() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        vm.open(show.setId)
        advanceUntilIdle()

        vm.chooseDeinterlace(true)
        advanceUntilIdle()
        assertTrue(vm.choices.value.deinterlace)
        assertEquals(true, handle.deinterlaceCalls.last())

        vm.chooseDeinterlace(false)
        advanceUntilIdle()
        assertFalse(vm.choices.value.deinterlace)
        assertEquals(false, handle.deinterlaceCalls.last())
        assertEquals(listOf("p1 key:show-x deinterlace=on", "p1 key:show-x deinterlace=null"), preferences.remembered)
    }

    /** A leak across titles would filter a film that is not interlaced. */
    @Test
    fun openingADifferentTitleSwitchesItOffAgain() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(
            handle = handle,
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show, film.setId to film)),
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseDeinterlace(true)
        advanceUntilIdle()

        vm.open(film.setId)
        advanceUntilIdle()

        assertFalse(vm.choices.value.deinterlace)
        assertEquals(false, handle.deinterlaceCalls.last())
    }

    /** The filter's video graph would tone-map HDR, so it is neither offered nor applied, whatever is remembered. */
    @Test
    fun anHdrTitleIsNotOfferedItAndNeverFiltered() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-h") to mapOf("deinterlace" to "on")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(hdrShow.setId to hdrShow)), preferences = preferences)

        vm.open(hdrShow.setId)
        advanceUntilIdle()

        assertFalse(vm.choices.value.deinterlaceOffered)
        assertEquals(null, vm.choices.value.deinterlaceRow)
        assertFalse(handle.deinterlaceCalls.any { it })
    }

    @Test
    fun anSdrTitleIsOfferedTheRow() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)))

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(false, vm.choices.value.deinterlaceRow)
    }
}
