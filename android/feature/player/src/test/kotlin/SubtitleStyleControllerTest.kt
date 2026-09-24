package player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/** A subtitle's size, backing and offset, remembered per show by [SubtitleStyleController]. */
@RunWith(RobolectricTestRunner::class)
class SubtitleStyleControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow

    @Test
    fun sizeBackingAndOffsetPersistPerShowAndResetIsARememberedChoiceToo() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.setSubtitleSize(135)
        vm.setSubtitleBacking("box")
        vm.nudgeSubtitleOffset(3) // +0.3s
        advanceUntilIdle()

        assertEquals(135, vm.choices.value.subtitleSizePercent)
        assertEquals("box", vm.choices.value.subtitleBacking)
        assertEquals(300L, vm.choices.value.subtitleOffsetMs)

        vm.resetSubtitleOffset()
        advanceUntilIdle()
        assertEquals(0L, vm.choices.value.subtitleOffsetMs)
        assertEquals(
            listOf("p1 key:show-x cue-offset=0.3", "p1 key:show-x cue-offset=0.0"),
            preferences.remembered.filter { it.contains("cue-offset") },
        )
    }

    /** Each value is saved on its own, as the web does: a size picked before the read answers must not write the default backing over a remembered one. */
    @Test
    fun aSizePickedBeforePreferencesLoadKeepsTheRememberedBacking() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("cue-backing" to "box")), gate)
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)),
            preferences = preferences,
        )
        vm.open(show.setId)

        vm.setSubtitleSize(135)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(135, vm.choices.value.subtitleSizePercent)
        assertEquals("box", vm.choices.value.subtitleBacking)
        assertEquals(listOf("p1 key:show-x cue-size=135"), preferences.remembered)
    }
}
