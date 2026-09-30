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
 * Exercises [SubtitleChoiceController] through [PlayerViewModel], the same
 * way [AudioChoiceControllerTest] does for audio — `plan.md`'s playback
 * rule: off by default, a forced track in the audio language showing
 * regardless, a profile default and a per-show remembered choice, and the
 * toggle-on rule. What survives a reopen lives in [SubtitleChoiceLifecycleTest].
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleChoiceControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow

    // -- off by default, forced still shows --

    @Test
    fun nothingRememberedOrPreferredStaysOffAndLoadsNoCues() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        assertEquals(emptyList(), vm.subtitleCues.value)
        assertTrue(trackSource.loadedFor.isEmpty(), "nothing remembered or preferred never fetches a track")
    }

    @Test
    fun aForcedTrackInTheAudioLanguageShowsWithNothingRememberedOrPreferred() = runTest {
        installMainDispatcher()
        val set = withSubtitles(show, alang = listOf("de")).copy(
            subtitles = listOf(track("de", forced = true, label = "Forced"), track("de", index = 1), track("en", sdh = true, index = 2)),
        )
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("Forced line"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to set)),
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        // Off is still what the picker shows selected — forced is not a pickable row.
        assertEquals(SUBTITLES_OFF, selected(vm))
        assertEquals(listOf(cue("Forced line")), vm.subtitleCues.value)
        assertEquals(listOf(show.setId to 0), trackSource.loadedFor)
    }

    @Test
    fun aForcedOnlyFileOffersNoPickerRowsButStillShowsItsCues() = runTest {
        installMainDispatcher()
        val set = withSubtitles(show, alang = listOf("de")).copy(subtitles = listOf(track("de", forced = true)))
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("Forced only"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to set)),
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.choices.value.subtitleOptions)
        assertEquals(listOf(cue("Forced only")), vm.subtitleCues.value)
        assertTrue(vm.choices.value.subtitleStyleVisible, "a forced-only file still gates style/offset open")
    }

    @Test
    fun anUnknownAudioLanguageNeverShowsAForcedTrack() = runTest {
        installMainDispatcher()
        val set = withSubtitles(show).copy(subtitles = listOf(track("de", forced = true)))
        val trackSource = FakeSubtitleTrackSource()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to set)),
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.subtitleCues.value)
        assertTrue(trackSource.loadedFor.isEmpty())
    }

    // -- remembered (per show) and preferred (profile default) --

    @Test
    fun aRememberedShowLanguageWinsOverTheProfileDefault() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 1) to listOf(cue("hi"))))
        val preferences = FakePlayerPreferences(
            mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "en"), ("p1" to "profile") to mapOf("subtitle" to "de")),
        )
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
    }

    @Test
    fun withNothingRememberedTheProfileDefaultApplies() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("hallo"))))
        val preferences = FakePlayerPreferences(mapOf(("p1" to "profile") to mapOf("subtitle" to "de")))
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
    fun aRememberedOffStaysOffEvenWithAProfileDefault() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences(
            mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "off"), ("p1" to "profile") to mapOf("subtitle" to "de")),
        )
        val trackSource = FakeSubtitleTrackSource()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        assertTrue(trackSource.loadedFor.isEmpty())
    }

    @Test
    fun pickingARowByHandRemembersItUnderTheShowsScope() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 1) to listOf(cue("hi"))))
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

    // -- the toggle-on rule --

    @Test
    fun togglingOnWithNothingElseSetLandsOnTheFirstRegularTrack() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("hallo"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        assertEquals(SUBTITLES_OFF, selected(vm))

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals("de", selected(vm))
        assertEquals(listOf(cue("hallo")), vm.subtitleCues.value)
    }

    @Test
    fun togglingOffThenOnAgainReturnsToTheLastRegularTrackChosenThisSession() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 1) to listOf(cue("hi"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseSubtitleLanguage("en")
        advanceUntilIdle()

        vm.toggleSubtitles() // off
        advanceUntilIdle()
        assertEquals(SUBTITLES_OFF, selected(vm))

        vm.toggleSubtitles() // on again
        advanceUntilIdle()

        assertEquals("en", selected(vm), "the last regular track this session wins over the first one")
    }

    @Test
    fun togglingOnAForcedOnlyTitleRemembersNothingAndLeavesTheForcedLinesAlone() = runTest {
        installMainDispatcher()
        val set = withSubtitles(show, alang = listOf("de")).copy(subtitles = listOf(track("de", forced = true)))
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("Forced only"))))
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to set)),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals(emptyList(), preferences.remembered)
        assertEquals(listOf(cue("Forced only")), vm.subtitleCues.value)
    }

    @Test
    fun togglingOnATitleWithNoTracksRemembersNothing() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals(emptyList(), preferences.remembered)
    }

    @Test
    fun togglingBeforeTheChoiceHasSettledDoesNothing() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val preferences = FakePlayerPreferences(gate = gate)
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList(), preferences.remembered)
    }

    // -- a pick that races the preference round trip --

    @Test
    fun aTrackPickedWhileThePreferenceRoundTripIsStillInFlightWinsOverTheLateResult() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 1) to listOf(cue("hi"))))
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "de")), gate)
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
            subtitleTrackSource = trackSource,
        )

        vm.open(show.setId)
        assertEquals(SUBTITLES_OFF, selected(vm), "nothing is chosen from tracks alone")
        assertTrue(trackSource.loadedFor.isEmpty(), "no fetch before the remembered choice is known")

        vm.chooseSubtitleLanguage("en") // reaffirmed by hand before "de" has even been read back

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
    }
}
