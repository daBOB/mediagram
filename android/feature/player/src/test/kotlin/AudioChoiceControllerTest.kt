package player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audio menu races the same singleton-player rules speed does
 * ([PlayerChoicesResetTest]), plus two more: it also depends on the file's
 * own tracks — which ExoPlayer only reports once it has read them, never
 * at open, possibly before or after the remembered language has loaded,
 * and sometimes preceded by a synthetic empty report this must not mistake
 * for the real answer — and it never pins a track this device cannot
 * decode. [RecordingPlayer] stands in for the real player enough to prove
 * which track (if any) an override actually lands on.
 *
 * Runs under Robolectric, not the bare JVM: a real `TrackGroup` logs
 * through `android.util.Log` the moment it is built, which the plain
 * unit-test `android.jar` stub answers with `null` rather than a value.
 */
@RunWith(RobolectricTestRunner::class)
class AudioChoiceControllerTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "30 Rock")
    private val film = fakeMediaSet(setId = "film1")

    /** German plus English, both decodable. [germanSelected] models whichever ExoPlayer's own selector already picked, independent of anything remembered. */
    private fun audioTracks(germanSelected: Boolean = false): Tracks {
        val german = Format.Builder().setLanguage("de").setChannelCount(6).setSampleMimeType("audio/ac3").build()
        val english = Format.Builder().setLanguage("en").setChannelCount(2).setSampleMimeType("audio/mp4a-latm").build()
        val group = Tracks.Group(
            TrackGroup(german, english),
            /* adaptiveSupported = */ false,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(germanSelected, !germanSelected),
        )
        return Tracks(listOf(group))
    }

    /** German tagged DTS with no decoder on a stock build, plus two the device can actually play. */
    private fun tracksWithAnUndecodableGerman(): Tracks {
        val german = Format.Builder().setLanguage("de").setChannelCount(6).setSampleMimeType("audio/vnd.dts").build()
        val english = Format.Builder().setLanguage("en").setChannelCount(2).setSampleMimeType("audio/mp4a-latm").build()
        val french = Format.Builder().setLanguage("fr").setChannelCount(2).setSampleMimeType("audio/mp4a-latm").build()
        val group = Tracks.Group(
            TrackGroup(german, english, french),
            false,
            intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(false, true, false),
        )
        return Tracks(listOf(group))
    }

    /** English, remembered elsewhere in these tests, plus a commentary track with no language tag at all. */
    private fun tracksWithAnUntaggedCommentary(): Tracks {
        val english = Format.Builder().setLanguage("en").setChannelCount(2).setSampleMimeType("audio/mp4a-latm").build()
        val commentary = Format.Builder().setLabel("Commentary").setChannelCount(2).setSampleMimeType("audio/mp4a-latm").build()
        val group = Tracks.Group(
            TrackGroup(english, commentary),
            false,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(true, false),
        )
        return Tracks(listOf(group))
    }

    /** A mocked [Player] that actually remembers whatever override was set on it, and hands back the [Player.Listener] this controller attached — the same style [DefaultPlayerHandleTest] uses for the other listener bridge. */
    private class RecordingPlayer {
        val player: Player = mockk(relaxed = true)
        private val listenerSlot = slot<Player.Listener>()
        private var current: TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

        /** Every parameters set on this player, in order — a test's way of seeing whether anything touched the transport at all. */
        val applied: MutableList<TrackSelectionParameters> = mutableListOf()

        init {
            every { player.addListener(capture(listenerSlot)) } just Runs
            every { player.trackSelectionParameters } answers { current }
            every { player.trackSelectionParameters = any() } answers {
                current = firstArg()
                applied += current
            }
        }

        fun emitTracks(tracks: Tracks) {
            listenerSlot.captured.onTracksChanged(tracks)
        }

        /** The track index a group's override selects, or null if this group carries none. */
        fun selectedTrackIndex(group: TrackGroup): Int? = current.overrides[group]?.trackIndices?.firstOrNull()
    }

    private fun attach(handle: FakePlayerHandle): RecordingPlayer {
        val recording = RecordingPlayer()
        handle.installPlayer(recording.player)
        return recording
    }

    @Test
    fun tracksArrivingAfterTheRememberedLanguageAppliesItAndBuildsTheMenu() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "en")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        advanceUntilIdle() // lets the controller attach its listener to the recording player

        vm.open(show.setId)
        advanceUntilIdle() // scope and the "en" preference resolve

        val tracks = audioTracks()
        recording.emitTracks(tracks)

        val group = tracks.groups[0].mediaTrackGroup
        assertEquals(1, recording.selectedTrackIndex(group), "English is trackIndex 1")
        assertEquals(listOf(false, true), vm.choices.value.audioOptions.map { it.selected })
    }

    @Test
    fun nothingRememberedLeavesExoPlayersOwnSelectionAlone() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(film.setId to film)))
        advanceUntilIdle()

        vm.open(film.setId)
        advanceUntilIdle()

        val tracks = audioTracks(germanSelected = true)
        recording.emitTracks(tracks)

        val group = tracks.groups[0].mediaTrackGroup
        assertEquals(null, recording.selectedTrackIndex(group), "nothing remembered — nothing pinned, ExoPlayer's own pick stands")
        assertEquals(listOf(true, false), vm.choices.value.audioOptions.map { it.selected })
    }

    @Test
    fun reopeningTheSameTitleKeepsTheChosenTrackWithoutTouchingTheTransportAgain() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "en")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        advanceUntilIdle()

        vm.open(show.setId)
        advanceUntilIdle()
        recording.emitTracks(audioTracks())
        val optionsBefore = vm.choices.value.audioOptions
        val appliedCountBefore = recording.applied.size

        vm.open(show.setId) // the rotation: same id, same screen re-running its effect
        advanceUntilIdle()

        assertEquals(appliedCountBefore, recording.applied.size, "no reload means nothing here should touch the transport at all")
        assertEquals(optionsBefore, vm.choices.value.audioOptions)
    }

    /**
     * The bug a real device hit: leaving the player calls `stop()`, which
     * already resets this controller once; opening the next title resets
     * it again and reloads the player for real, and `ExoPlayerImpl` fires
     * a synthetic `Tracks.EMPTY` clearing itself for the new item strictly
     * before the real probe of it answers. Mistaking that for "this title
     * has no audio" made every title after the first — and any reopen,
     * since `stop()` already cleared the session that made a reopen "the
     * same title" — permanently lose its menu.
     */
    @Test
    fun aSyntheticEmptyTracksEventBetweenTitlesDoesNotSuppressTheRealOnesThatFollow() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "en")))
        val vm = buildViewModel(
            handle = handle,
            catalogRepository = FakeCatalogRepository(mapOf(film.setId to film, show.setId to show)),
            preferences = preferences,
        )
        advanceUntilIdle()

        vm.open(film.setId)
        advanceUntilIdle()
        recording.emitTracks(audioTracks())

        vm.stop()
        vm.open(show.setId)
        advanceUntilIdle() // show's own scope and "en" preference resolve

        recording.emitTracks(Tracks.EMPTY) // ExoPlayerImpl's own clearing event for the reload
        val tracks = audioTracks() // show's real tracks, arriving right after
        recording.emitTracks(tracks)

        val group = tracks.groups[0].mediaTrackGroup
        assertEquals(1, recording.selectedTrackIndex(group), "English, remembered for the show, not lost to the empty event")
        assertEquals(listOf(false, true), vm.choices.value.audioOptions.map { it.selected })
    }

    @Test
    fun anUndecodableTrackIsNeverOfferedOrPinnedEvenWhenItsLanguageIsRemembered() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        // Remembered "de" names a track this build has no decoder for.
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "de")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        advanceUntilIdle()

        vm.open(show.setId)
        advanceUntilIdle()
        val tracks = tracksWithAnUndecodableGerman()
        recording.emitTracks(tracks)

        val options = vm.choices.value.audioOptions
        assertEquals(setOf("en", "fr"), options.map { it.language }.toSet())
        val group = tracks.groups[0].mediaTrackGroup
        assertEquals(null, recording.selectedTrackIndex(group), "the remembered language names a track this device cannot decode — nothing is pinned")
    }

    @Test
    fun pickingAnUntaggedTrackAppliesButDoesNotOverwriteTheSavedChoice() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "en")))
        val vm = buildViewModel(handle = handle, catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)), preferences = preferences)
        advanceUntilIdle()

        vm.open(show.setId)
        advanceUntilIdle()
        recording.emitTracks(tracksWithAnUntaggedCommentary())

        val commentary = vm.choices.value.audioOptions.first { it.language == null }
        vm.chooseAudioTrack(commentary)
        advanceUntilIdle()

        assertTrue(vm.choices.value.audioOptions.first { it.language == null }.selected, "honoured for this session")
        assertTrue(preferences.remembered.isEmpty(), "an untagged pick must not blank out the good 'en' choice on record")
    }

    /**
     * Tracks can arrive before the scope has even resolved — ExoPlayer's own
     * probe and a core round trip race each other with no ordering
     * guarantee either way. A pick made in that window has to survive the
     * remembered language landing afterwards, the same as [PlayerChoicesResetTest]
     * already proves for speed.
     */
    @Test
    fun aTrackPickedWhileTheScopeIsStillResolvingWinsOverTheLateRememberedResult() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val gate = CompletableDeferred<Unit>()
        // Remembered "en" — if this were ever allowed to apply, it would
        // flip the selection away from the German the viewer is about to
        // reaffirm by hand.
        val preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("audio" to "en")))
        val vm = buildViewModel(
            handle = handle,
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to show), gate),
            preferences = preferences,
        )
        advanceUntilIdle()

        vm.open(show.setId) // resolve() suspends on the gate, mid-flight
        val tracks = audioTracks(germanSelected = true) // ExoPlayer's own pick, before anything is remembered
        recording.emitTracks(tracks)

        val german = vm.choices.value.audioOptions.first { it.language == "de" }
        assertTrue(german.selected, "the provisional menu already reflects ExoPlayer's own pick")
        vm.chooseAudioTrack(german) // reaffirmed by hand before the remembered "en" has even been read

        gate.complete(Unit)
        advanceUntilIdle()

        val group = tracks.groups[0].mediaTrackGroup
        assertEquals(0, recording.selectedTrackIndex(group), "the hand-picked German, not the remembered English")
        assertEquals(listOf(true, false), vm.choices.value.audioOptions.map { it.selected })
        assertEquals(listOf("p1 key:show-x audio=de"), preferences.remembered)
    }

    @Test
    fun releaseDetachesTheListenerFromThePlayer() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val recording = attach(handle)
        val controller = AudioChoiceController(backgroundScope, handle, FakePlayerPreferences()) {}
        runCurrent()
        verify(exactly = 1) { recording.player.addListener(any()) }

        controller.release()

        verify(exactly = 1) { recording.player.removeListener(any()) }
    }
}
