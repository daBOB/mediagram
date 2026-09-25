package player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import playback.Framing
import kotlin.test.Test
import kotlin.test.assertEquals

/** How this viewer framed the open title, remembered per show by [FramingController] — the same shape [SubtitleStyleController] uses for size, backing and offset. */
class FramingControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "30 Rock")
    private val film = fakeMediaSet(setId = "film1")

    @Test
    fun openingAShowWithARememberedFramingAppliesIt() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("framing" to "16:9")))
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(Framing.RATIO_16_9, vm.choices.value.framing)
    }

    @Test
    fun choosingAFramingRemembersItUnderTheOpenShowsScope() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        vm.open(show.setId)
        advanceUntilIdle()

        vm.chooseFraming(Framing.FILL)
        advanceUntilIdle()

        assertEquals(Framing.FILL, vm.choices.value.framing)
        assertEquals(listOf("p1 key:show-x framing=fill"), preferences.remembered)
    }

    /** A leak across titles is exactly the bug this whole store exists to prevent — see [PlayerChoices]. */
    @Test
    fun openingADifferentTitleDropsTheLastShowsFraming() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show, film.setId to film)),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseFraming(Framing.RATIO_4_3)
        advanceUntilIdle()

        vm.open(film.setId)
        advanceUntilIdle()

        assertEquals(Framing.FIT, vm.choices.value.framing)
    }

    /** A rotation reopens the *same* title — nothing here should reset, the same rule speed's own reopen test proves. */
    @Test
    fun reopeningTheSameTitleKeepsAHandPickedFraming() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseFraming(Framing.FILL)

        vm.open(show.setId) // the rotation: same id, same screen re-running its effect

        assertEquals(Framing.FILL, vm.choices.value.framing)
    }

    /** A framing picked by hand while the scope is still resolving must win over the remembered one landing after it, the same race [PlayerChoicesResetTest] proves for speed. */
    @Test
    fun aFramingPickedWhileTheScopeIsStillResolvingWinsOverTheLateResult() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("framing" to "16:9")), gate)
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)

        vm.open(show.setId) // resolve() suspends on the gate, mid-flight
        vm.chooseFraming(Framing.RATIO_4_3) // picked by hand before the remembered 16:9 has even been read

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(Framing.RATIO_4_3, vm.choices.value.framing)
        assertEquals(listOf("p1 key:show-x framing=4:3"), preferences.remembered)
    }
}
