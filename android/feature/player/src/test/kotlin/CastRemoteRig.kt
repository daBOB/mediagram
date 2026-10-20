package player

import data.CoreTogetherChannel
import data.DefaultTogetherLive
import data.LibraryEvents
import data.settings.InMemoryLibrarySettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import model.together.Cast
import model.together.CastState
import model.together.CastTracks
import model.together.Session
import model.together.Track
import model.together.parseSession
import model.together.writeSession
import player.together.EP1
import player.together.EP2
import player.together.NOW
import player.together.TV
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherMessage

/** The channel message the cast is. */
internal const val CAST_ID = 970

/** The TV playing [EP1] for a tablet's cast, 100 s in at [NOW], offering an English soundtrack and German subtitles. */
internal val CAST_ON_TV =
    Session(
        "s-cast", EP1, playing = true, pos = 100.0, rate = 1.0, at = NOW, by = TV.screen, members = listOf(TV),
        ended = false,
        cast =
            Cast(
                from = "Tablet", profile = "A", state = CastState.ON, tv = TV.screen,
                tracks = CastTracks(listOf(Track("a1", "English")), listOf(Track("de", "German"))), audio = "a1",
            ),
    )

/**
 * A phone's remote over the real `DefaultTogetherLive` on a [FakeCore]
 * channel holding [CAST_ON_TV] as message [CAST_ID]. The clock moves only
 * with the test's virtual time. The catalog holds [EP1] ("Pilot", with a
 * backdrop) and [EP2] ("Second", with a poster only), 1500 s each.
 */
internal class CastRemoteRig(
    private val test: TestScope,
) {
    val core = FakeCore().apply { togetherMessages[CAST_ID] = writeSession(CAST_ON_TV) }
    /** The core's push stream: someone subscribed to it is a connection held open. */
    val events = MutableSharedFlow<LibraryEvent>()
    private val now = { NOW + test.testScheduler.currentTime }
    val live =
        DefaultTogetherLive(
            CoreTogetherChannel(
                FakeCoreProvider(core),
                InMemoryLibrarySettings().apply { runBlocking { write("library") } },
                LibraryEvents { events },
            ),
            test.backgroundScope,
            durationOf = { 1500.0 },
            now = now,
        )
    private val catalog =
        FakeCatalogRepository(
            mapOf(
                EP1 to fakeMediaSet(EP1, title = "Pilot", durationSecs = 1500).copy(backdropPath = "/b/ep1.jpg"),
                EP2 to fakeMediaSet(EP2, title = "Second", durationSecs = 1500, posterPath = "/p/ep2.jpg"),
            ),
        )

    // The model's scope launches onto Main from the start.
    init {
        test.installMainDispatcher()
    }

    val vm = CastRemoteViewModel(live, catalog, "Phone", now)

    /** The last edit sent to the channel. */
    val sent: Session get() = parseSession(core.togetherEdits.last().text)!!

    /** The cast's message as the channel holds it now. */
    fun message(): Session = parseSession(core.togetherMessages.getValue(CAST_ID))!!

    /** The TV's edit [session], as the channel pushes it. */
    suspend fun fromTv(session: Session) {
        core.togetherMessages[CAST_ID] = writeSession(session)
        core.togetherHeardMessage = TogetherMessage(CAST_ID, writeSession(session))
        events.emit(LibraryEvent.TOGETHER)
        test.runCurrent()
    }
}

/** The remote opened on [CAST_ON_TV] as the bar or Play on TV opens it, its join sent; Next episode follows [run]. */
internal suspend fun TestScope.castRemote(run: List<String> = listOf(EP1, EP2)): CastRemoteRig =
    CastRemoteRig(this).apply {
        live.refresh()
        vm.open(CAST_ID) { run }
        runCurrent()
    }
