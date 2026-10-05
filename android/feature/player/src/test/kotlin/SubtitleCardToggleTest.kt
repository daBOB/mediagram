package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The card's CC button over [toggleSubtitles]: on brings back the language
 * last chosen for the title, and with none it picks what the player picks
 * today — the web's own `c` key rule. With no regular track it has nothing
 * to act on, which the card draws as disabled off [PlayerChoices.ccVisible].
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleCardToggleTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow

    /** "Off" remembered for the show is no language to bring back: on falls through to the profile's own. */
    @Test
    fun onWithNoRememberedLanguagePicksTheProfileLanguage() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences(
            mapOf(("p1" to "key:show-x") to mapOf("subtitle" to SUBTITLES_OFF), ("p1" to "profile") to mapOf("subtitle" to "en")),
        )
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        assertEquals(SUBTITLES_OFF, selected(vm))

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals("en", selected(vm))
    }

    @Test
    fun onAfterOffBringsBackTheLanguageChosenForTheTitle() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "en"))),
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.toggleSubtitles() // off
        vm.toggleSubtitles() // on
        advanceUntilIdle()

        assertEquals("en", selected(vm), "the show's own language, not the file's first track")
    }

    @Test
    fun aTitleWithARegularTrackHasCcToActOn() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))))
        vm.open(show.setId)
        advanceUntilIdle()

        assertTrue(vm.choices.value.ccVisible)
    }

    @Test
    fun aTitleWithNoRegularTrackHasNothingForCcToActOn() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)))
        vm.open(show.setId)
        advanceUntilIdle()

        assertFalse(vm.choices.value.ccVisible)
        vm.toggleSubtitles()
        advanceUntilIdle()
        assertFalse(vm.choices.value.subtitlesOn)
    }
}
