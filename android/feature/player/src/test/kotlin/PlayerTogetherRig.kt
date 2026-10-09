package player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import data.CoreTogetherChannel
import data.DefaultTogetherLive
import data.LibraryEvents
import data.settings.InMemoryLibrarySettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import model.together.Session
import model.together.parseSession
import model.together.writeSession
import player.together.ViewerRelay
import testing.FakeCore
import testing.FakeCoreProvider
import testing.WatchStateFixture
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherMessage

/** A phone, to the surface checks: no television mode, and narrower than a tablet. */
internal fun phoneContext(): Context =
    mockk(relaxed = true) {
        every { getSystemService(Context.UI_MODE_SERVICE) } returns
            mockk<UiModeManager> { every { currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL }
    }

/**
 * A [PlayerViewModel] in watch together, over the real `DefaultTogetherLive`
 * on a [FakeCore] channel and a [FakePlayerHandle] whose player is a mock,
 * ready and playing ten seconds in. The catalog holds `film-1`, `e1` and
 * `e2`. Sessions are stamped with the real clock, which the ViewModel's
 * controller reads.
 */
internal class PlayerTogetherRig(
    private val test: TestScope,
    watch: WatchStateFixture = WatchStateFixture(),
    catalog: FakeCatalogRepository = FakeCatalogRepository(listOf("film-1", "e1", "e2").associateWith { fakeMediaSet(it) }),
) {
    val core = FakeCore()
    private val events = MutableSharedFlow<LibraryEvent>()
    val relay = ViewerRelay()
    val player =
        mockk<Player>(relaxed = true) {
            every { playbackState } returns Player.STATE_READY
            every { playWhenReady } returns true
            every { playbackParameters } returns PlaybackParameters.DEFAULT
        }
    val handle = FakePlayerHandle().also {
        it.installPlayer(player)
        it.fakePositionMs = 10_000L
    }
    val live =
        DefaultTogetherLive(
            CoreTogetherChannel(FakeCoreProvider(core), InMemoryLibrarySettings().apply { runBlocking { write("library") } }, LibraryEvents { events }),
            test.backgroundScope,
            durationOf = { null },
        )
    val vm = buildViewModel(handle, watch.repository, catalogRepository = catalog, together = live, relay = relay, context = phoneContext())

    val edits: List<Session> get() = core.togetherEdits.map { parseSession(it.text)!! }

    fun put(
        id: Int,
        session: Session,
    ) {
        core.togetherMessages[id] = writeSession(session)
    }

    fun message(id: Int): Session = parseSession(core.togetherMessages.getValue(id))!!

    suspend fun push(
        id: Int,
        session: Session,
    ) {
        core.togetherHeardMessage = TogetherMessage(id, writeSession(session))
        events.emit(LibraryEvent.TOGETHER)
        test.runCurrent()
    }

    /** Opens [setId] in [run] and presses Watch together: this screen starts one, or joins the one live. */
    fun inASession(
        setId: String = "film-1",
        run: List<String> = emptyList(),
    ) {
        vm.open(setId, run)
        test.runCurrent()
        vm.onTogetherButton()
        test.runCurrent()
    }

    /**
     * Clears [vm] as its owner going away does: after its scope has been
     * cancelled. A test still in a session when it ends calls this: the drift
     * check runs on the ViewModel's own scope, and `runTest`'s closing
     * advance would otherwise run it for ever.
     */
    fun clear() {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }
        ViewModelProvider(store, factory)[PlayerViewModel::class.java]
        store.clear()
    }
}
