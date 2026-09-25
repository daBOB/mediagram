package player

import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.MediaSet
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises [SubtitleChoiceController] through [PlayerViewModel], the same
 * way [AudioChoiceControllerTest] does for audio — but this choice races
 * only one async source (the core round trip), never a `Player`, so the
 * `Tracks.EMPTY` case below is a guard against a *future* regression rather
 * than one this controller could hit today; see the class doc for why.
 * Runs under Robolectric for the one test that mocks a real `Player`.
 */
/**
 * What [SubtitleChoiceController] keeps and drops across opens — a rotation
 * keeps the choice, a different title starts from its own default — and its
 * standing independence from ExoPlayer's own `Tracks` events. Robolectric
 * for the one test that mocks a real `Player`.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleChoiceLifecycleTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow
    private val film = fakeMediaSet(setId = "film1")

    // -- reset on a new title, kept across the same one --

    @Test
    fun reopeningTheSameTitleKeepsTheChosenLanguageWithoutReloading() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "en") to listOf(cue("hi"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseSubtitleLanguage("en")
        advanceUntilIdle()
        val loadsBefore = trackSource.loadedFor.size

        vm.open(show.setId) // the rotation: same id, same screen re-running its effect

        assertEquals(loadsBefore, trackSource.loadedFor.size, "no reload means nothing here should fetch again")
        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
    }

    @Test
    fun openingADifferentTitleResetsToItsOwnDefault() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(
            mapOf((show.setId to "en") to listOf(cue("hi")), (film.setId to "fr") to listOf(cue("bonjour"))),
        )
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"), film.setId to withSubtitles(film, "fr"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseSubtitleLanguage("en")
        advanceUntilIdle()

        vm.open(film.setId) // a genuinely different title
        advanceUntilIdle()

        assertEquals("fr", selected(vm))
        assertEquals(listOf(cue("bonjour")), vm.subtitleCues.value)
    }

    // -- a title with none --

    @Test
    fun aTitleWithNoSubtitlesOffersNoRowsAtAll() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(film.setId to film)))

        vm.open(film.setId)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.choices.value.subtitleOptions)
    }

    // -- independence from ExoPlayer's own track lifecycle --

    /**
     * Nothing here ever listens to a `Player`; this documents that as a
     * standing guarantee rather than an accident, since the audio menu's
     * own equivalent state was corrupted by exactly this event
     * (`AudioChoiceControllerTest`'s C1). A future change that wired
     * subtitle state to `Player.Listener` would reintroduce that bug class;
     * this fails first.
     */
    @Test
    fun subtitleStateIsUntouchedByExoPlayersOwnTracksEvents() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val mockPlayer = mockk<Player>(relaxed = true)
        val listenerSlot = slot<Player.Listener>()
        every { mockPlayer.addListener(capture(listenerSlot)) } just Runs
        every { mockPlayer.trackSelectionParameters } returns TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        every { mockPlayer.trackSelectionParameters = any() } just Runs
        handle.installPlayer(mockPlayer)

        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to "de") to listOf(cue("hallo"))))
        val vm = buildViewModel(
            handle = handle,
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        val cuesBefore = vm.subtitleCues.value
        assertEquals(listOf(cue("hallo")), cuesBefore)

        listenerSlot.captured.onTracksChanged(Tracks.EMPTY) // ExoPlayerImpl's own reload-clearing event

        assertEquals(cuesBefore, vm.subtitleCues.value)
    }
}
