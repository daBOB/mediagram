package data

import data.settings.LibrarySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import model.together.Member
import model.together.Playback
import model.together.Session
import model.together.parseSession
import model.together.writeSession
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherEdit
import uniffi.mediagram_core.TogetherMessage

const val NOW = 1_791_500_000_000L
const val HOUR_MS = 60 * 60 * 1000L
val A = Member("tab-a", "Tablet")
val B = Member("phone-b", "Phone")
val TV = Member("tv-1", "TV")
val FILM = Playback("film-1", playing = true, pos = 10.0, rate = 1.0)

/** A session another screen, the TV, started on [FILM] just now. */
val TV_SESSION = Session("s-tv", FILM.set, FILM.playing, FILM.pos, FILM.rate, NOW, TV.screen, listOf(TV), ended = false)

/**
 * [DefaultTogetherLive] over a [FakeCore] channel, on the test's own
 * scheduler: nothing reaches Telegram, and time moves only when the test
 * moves it. The clock stands at [NOW]. [FILM] is 100 s long; any other
 * title's length is unknown.
 */
class TogetherHarness(
    private val scope: TestScope,
    settings: LibrarySettings = settingsWithAChosenLibrary(),
) {
    val core = FakeCore()
    val events = MutableSharedFlow<LibraryEvent>()
    val live =
        DefaultTogetherLive(
            CoreTogetherChannel(FakeCoreProvider(core), settings, LibraryEvents { events }),
            scope.backgroundScope,
            durationOf = { if (it == FILM.set) 100.0 else null },
            now = { NOW },
        )

    /** Every session the channel was asked to hold, in order, whatever it answered. */
    val edits: List<Session> get() = core.togetherEdits.map { parseSession(it.text)!! }

    fun message(id: Int): Session = parseSession(core.togetherMessages.getValue(id))!!

    fun put(
        id: Int,
        session: Session,
    ) {
        core.togetherMessages[id] = writeSession(session)
    }

    /** Holds the next edit on its way until the test completes the answer. */
    fun holdEdit(): CompletableDeferred<TogetherEdit> = CompletableDeferred<TogetherEdit>().also { core.togetherEditOutcomes += it }

    fun answerEdit(outcome: TogetherEdit) {
        core.togetherEditOutcomes += CompletableDeferred(outcome)
    }

    suspend fun start(): Int = live.start(A, FILM).live.id

    /** Collects [DefaultTogetherLive.live], as a screen on display does: what keeps listening open. */
    fun watch(): Job = scope.backgroundScope.launch { live.live.collect {} }.also { scope.runCurrent() }

    /** A push of [session] as message [id], as the core hears it. Heard only while someone [watch]es. */
    suspend fun push(
        id: Int,
        session: Session,
    ) = push(TogetherMessage(id, writeSession(session)))

    suspend fun push(message: TogetherMessage) {
        core.togetherHeardMessage = message
        events.emit(LibraryEvent.TOGETHER)
        scope.runCurrent()
    }
}
