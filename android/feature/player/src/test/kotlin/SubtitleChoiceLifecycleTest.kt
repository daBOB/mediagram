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
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [SubtitleChoiceController] keeps and drops across opens — a rotation
 * keeps the choice, a different title starts from its own default (off,
 * nothing remembered or preferred there) — and its standing independence
 * from ExoPlayer's own `Tracks` events. Robolectric for the one test that
 * mocks a real `Player`.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleChoiceLifecycleTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow
    private val film = fakeMediaSet(setId = "film1")

    // -- reset on a new title, kept across the same one --

    @Test
    fun reopeningTheSameTitleKeepsTheChosenTrackWithoutReloading() = runTest {
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
        val loadsBefore = trackSource.loadedFor.size

        vm.open(show.setId) // the rotation: same id, same screen re-running its effect

        assertEquals(loadsBefore, trackSource.loadedFor.size, "no reload means nothing here should fetch again")
        assertEquals("en", selected(vm))
        assertEquals(listOf(cue("hi")), vm.subtitleCues.value)
    }

    @Test
    fun openingADifferentTitleResetsToItsOwnDefault() = runTest {
        installMainDispatcher()
        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 1) to listOf(cue("hi"))))
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"), film.setId to withSubtitles(film, "fr"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseSubtitleLanguage("en")
        advanceUntilIdle()

        vm.open(film.setId) // a genuinely different title, nothing remembered or preferred for it
        advanceUntilIdle()

        assertEquals(SUBTITLES_OFF, selected(vm))
        assertEquals(emptyList(), vm.subtitleCues.value)
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
     * Nothing here ever listens to a `Player` for the subtitle choice
     * itself; this documents that as a standing guarantee rather than an
     * accident, since the audio menu's own equivalent state was corrupted
     * by exactly this event (see `AudioChoiceControllerTest`). A future
     * change that wired subtitle state to `Player.Listener` would
     * reintroduce that bug class; this fails first.
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

        val trackSource = FakeSubtitleTrackSource(mapOf((show.setId to 0) to listOf(cue("hallo"))))
        val vm = buildViewModel(
            handle = handle,
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))),
            subtitleTrackSource = trackSource,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        vm.chooseSubtitleLanguage("de")
        advanceUntilIdle()
        val cuesBefore = vm.subtitleCues.value
        assertEquals(listOf(cue("hallo")), cuesBefore)

        listenerSlot.captured.onTracksChanged(Tracks.EMPTY) // ExoPlayerImpl's own reload-clearing event

        assertEquals(cuesBefore, vm.subtitleCues.value)
    }
}
