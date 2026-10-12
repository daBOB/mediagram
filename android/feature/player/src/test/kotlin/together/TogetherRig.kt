package player.together

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import data.CoreTogetherChannel
import data.DefaultTogetherLive
import data.LibraryEvents
import data.TogetherLive
import data.settings.InMemoryLibrarySettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import model.together.Member
import model.together.Session
import model.together.parseSession
import model.together.writeSession
import player.FakePlayerHandle
import player.fakeMediaSet
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherMessage

const val NOW = 1_791_500_000_000L
const val EP1 = "ep-1"
const val EP2 = "ep-2"

/** A title this profile may not see, as a kids profile does not see one over its limit. */
const val HIDDEN = "film-16"
val TV = Member("tv-1", "TV")
val PHONE = Member("phone-1", "Phone")

/** A session the TV started on [EP1] at [NOW], ten seconds in and playing. */
val TV_SESSION = Session("s-tv", EP1, playing = true, pos = 10.0, rate = 1.0, at = NOW, by = TV.screen, members = listOf(TV), ended = false)

/**
 * A [TogetherController] over the real `DefaultTogetherLive` on a [FakeCore]
 * channel, driving a [FakePlayerHandle] whose player is a mock: nothing
 * reaches Telegram, and time moves only when the test moves it. The clock
 * reads [NOW] plus the virtual time passed. [EP1] and [EP2] run 1500 s;
 * [HIDDEN] is not in this profile's catalog. The player stands ten seconds
 * into whatever is open, ready and playing at 1×, until a test moves it.
 */
internal class TogetherRig(
    private val test: TestScope,
    /** What the controller is handed in place of [live]: a test's stand-in for one answer. */
    wrap: (TogetherLive) -> TogetherLive = { it },
) {
    val core = FakeCore()
    val provider = FakeCoreProvider(core)
    /** The core's push stream: someone subscribed to it is a connection held open. */
    val events = MutableSharedFlow<LibraryEvent>()
    val handle = FakePlayerHandle()
    val player = mockk<Player>(relaxed = true)
    var state = Player.STATE_READY
    var speed = 1f

    /** Every title the controller asked the player to open, in order. */
    val opened = mutableListOf<String>()
    private val sets = listOf(EP1, EP2).associateWith { fakeMediaSet(it, durationSecs = 1500) }

    init {
        every { player.playbackState } answers { state }
        every { player.playWhenReady } returns true
        every { player.playbackParameters } answers { PlaybackParameters(speed) }
        handle.installPlayer(player)
        handle.fakePositionMs = 10_000L
    }

    fun now(): Long = NOW + test.testScheduler.currentTime

    val live =
        DefaultTogetherLive(
            CoreTogetherChannel(provider, InMemoryLibrarySettings().apply { runBlocking { write("library") } }, LibraryEvents { events }),
            test.backgroundScope,
            durationOf = { sets[it]?.durationSecs?.toDouble() },
            now = ::now,
        )

    val controller =
        TogetherController(
            wrap(live),
            handle,
            openTitle = { opened += it },
            titleOf = { sets[it] },
            label = { "Tablet" },
            scope = CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
            now = ::now,
        )

    /** Every session the channel was asked to hold, in order, whatever it answered. */
    val edits: List<Session> get() = core.togetherEdits.map { parseSession(it.text)!! }

    fun put(
        id: Int,
        session: Session,
    ) {
        core.togetherMessages[id] = writeSession(session)
    }

    /** Message [id] as the channel holds it now. */
    fun message(id: Int): Session = parseSession(core.togetherMessages.getValue(id))!!

    /** Another screen's edit [session] to message [id], as the core hears it. */
    suspend fun push(
        id: Int,
        session: Session,
    ) {
        core.togetherHeardMessage = TogetherMessage(id, writeSession(session))
        events.emit(LibraryEvent.TOGETHER)
        test.runCurrent()
    }

    /** Opens [set], lets the read on opening answer, and presses Watch together. */
    fun openAndPress(set: String = EP1) {
        controller.open(set)
        test.runCurrent()
        controller.onButton()
        test.runCurrent()
    }

    /** In [TV_SESSION] as message 950, joined from [EP1]. */
    fun joined(): TogetherRig {
        put(950, TV_SESSION)
        openAndPress()
        return this
    }
}
