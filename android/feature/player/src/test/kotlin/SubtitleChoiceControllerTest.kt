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
import kotlin.test.assertTrue

/**
 * Which language [SubtitleChoiceController] turns on, through
 * [PlayerViewModel] the same way [AudioChoiceControllerTest] does for audio:
 * the default rule, a pick by hand, and a pick racing the preference read.
 * What survives a reopen lives in [SubtitleChoiceLifecycleTest].
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleChoiceControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow

    // -- the default rule --

    @Test
    fun nothingRememberedComesUpOnTheFirstLanguage() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals("de", selected(vm))
        assertEquals(listOf(cue("hallo")), vm.subtitleCues.value)
    }

    @Test
    fun aRememberedOffStaysOffAndLoadsNoCues() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "off")))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        assertEquals(emptyList(), vm.subtitleCues.value)
        assertTrue(trackSource.loadedFor.isEmpty(), "off never fetches a track")
    }

    /**
     * The real preference read suspends, so the languages always arrive
     * first. A default applied from them alone would fetch a track the
     * remembered "off" then discards — and, if that fetch won, flash its cues
     * on a show the viewer switched off.
     */
    @Test
    fun aRememberedOffStaysOffEvenWhenThePreferenceReadIsSlow() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "off")), gate)
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.subtitleCues.value)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        assertTrue(trackSource.loadedFor.isEmpty(), "off never fetches a track, however slowly it is read back")
    }

    @Test
    fun rePickingTheLanguageAlreadyOnDoesNotFetchItAgain() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.chooseSubtitleLanguage("de")
        advanceUntilIdle()

        assertEquals(listOf(show.setId to "de"), trackSource.loadedFor)
        assertEquals(listOf(cue("hallo")), vm.subtitleCues.value)
    }

    // -- picking, with a fallback --

    @Test
    fun aRememberedLanguageThisFileLostFallsBackToTheFirstOne() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "fr")))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals("de", selected(vm))
    }

    @Test
    fun pickingARowByHandRemembersItUnderTheShowsScope() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "en") to listOf(cue("hi"))))
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.chooseSubtitleLanguage("en")
        advanceUntilIdle()

        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
        assertEquals(listOf("p1 key:show-x subtitle=en"), preferences.remembered)
    }

    // -- a pick that races the resolve --

    /**
     * The window this guards is real, not artificial: [PlayerChoicesController.resolve]
     * calls `subtitleChoice.onLanguagesKnown` the moment the set resolves —
     * before its own preference round trip — so the Subtitles section is
     * already showing real rows (and so pickable) while that round trip is
     * still in flight. The gate sits on [FakePlayerPreferences.load], the
     * suspension point that follows [SubtitleChoiceController.onLanguagesKnown],
     * not on the catalog lookup that precedes it.
     */
    @Test
    fun aLanguagePickedWhileThePreferenceRoundTripIsStillInFlightWinsOverTheLateResult() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "en") to listOf(cue("hi"))))
        // Remembered "de" — if this were ever allowed to apply, it would
        // flip the pick away from the language reaffirmed by hand below.
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "de")), gate)
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId) // languages are known (Unconfined runs eagerly); the preference load is gated, mid-flight
        assertEquals(SUBTITLES_OFF, selected(vm), "nothing is chosen from languages alone")
        assertTrue(trackSource.loadedFor.isEmpty(), "no fetch before the remembered choice is known")

        vm.chooseSubtitleLanguage("en") // reaffirmed by hand before "de" has even been read back

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
    }
}
