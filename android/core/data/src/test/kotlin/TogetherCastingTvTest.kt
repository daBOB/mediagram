package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CastState
import model.together.CastTracks
import model.together.Member
import model.together.Session
import model.together.Track
import model.together.accepted
import model.together.busied
import model.together.offered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Telegram's limit on a message's text. */
private const val MESSAGE_MAX = 4096

/** The TV answers an offer and keeps its track list; a remote chooses a track. */
class TogetherCastingTvTest {
    private val offer: Session = offered("s-offer", B, FILM, "kid", "home", NOW)
    private val tv2 = Member("tv-2", "Kitchen")

    /** The TV has heard [offer] as message 11. */
    private suspend fun TogetherHarness.offered() {
        put(11, offer)
        watch()
        push(11, offer)
    }

    @Test
    fun anAcceptJoinsTheTvAndWritesIt() =
        runTest {
            val h = TogetherHarness(this)
            h.put(11, offer)

            val live = h.live.accept(11, TV)
            runCurrent()

            assertEquals(CastState.ON, live?.session?.cast?.state)
            val written = h.edits.single()
            assertEquals(TV.screen, written.cast?.tv)
            assertTrue(written.members.any { it.screen == TV.screen && !it.remote })
        }

    @Test
    fun acceptLosesToAnotherTv() =
        runTest {
            val h = TogetherHarness(this)
            h.put(11, accepted(offer, tv2, NOW)!!)

            assertNull(h.live.accept(11, TV))
            runCurrent()
            assertEquals(emptyList(), h.edits)
        }

    @Test
    fun anAcceptHeardWhileReadingKeepsTheOtherTvsCast() =
        runTest {
            val h = TogetherHarness(this)
            h.offered()
            val read = CompletableDeferred<Unit>()
            h.core.togetherReadGate = read
            val taking = async { h.live.accept(11, TV) }
            runCurrent()
            h.push(11, accepted(offer, tv2, NOW + 500)!!)
            read.complete(Unit)
            runCurrent()

            assertNull(taking.await())
            assertEquals(emptyList(), h.edits)
        }

    @Test
    fun aRequestAnotherTvAnsweredIsNotTaken() =
        runTest {
            val h = TogetherHarness(this)
            h.put(11, busied(offer, tv2.screen, "Dune (2021)"))

            assertNull(h.live.accept(11, TV))
            runCurrent()
            assertEquals(emptyList(), h.edits)
        }

    @Test
    fun aBusyTvWritesItsTitle() =
        runTest {
            val h = TogetherHarness(this)
            h.offered()
            h.live.busy(11, TV.screen, "Dune (2021)")
            runCurrent()

            val written = h.edits.single()
            assertEquals(CastState.BUSY, written.cast?.state)
            assertEquals("Dune (2021)", written.cast?.note)
        }

    @Test
    fun aRefusingTvWritesItsReason() =
        runTest {
            val h = TogetherHarness(this)
            h.offered()
            h.live.refuse(11, TV.screen, "too young")
            runCurrent()

            assertEquals(CastState.REFUSED, h.edits.single().cast?.state)
            assertEquals("too young", h.edits.single().cast?.note)
        }

    @Test
    fun tracksStayUnderTheMessageLimit() =
        runTest {
            val h = TogetherHarness(this)
            h.put(11, offer)
            h.live.accept(11, TV)
            val long = "a track label far longer than any screen shows ".repeat(4)
            val text = List(40) { Track("t$it", "$long$it") }

            h.live.tracks(11, TV.screen, CastTracks(listOf(Track("a1", long)), text), audio = "a1", text = "")
            runCurrent()

            assertEquals("a1", h.edits.last().cast?.audio)
            assertTrue(h.core.togetherEdits.last().text.length < MESSAGE_MAX)
        }

    @Test
    fun aRemoteChoiceKeepsTheOtherTrack() =
        runTest {
            val h = TogetherHarness(this)
            h.put(11, offer)
            h.live.accept(11, TV)
            val tracks = CastTracks(listOf(Track("a1", "English")), listOf(Track("de", "Deutsch")))
            h.live.tracks(11, TV.screen, tracks, audio = "a1", text = "")
            h.live.choose(11, B.screen, text = "de")
            runCurrent()

            val cast = h.message(11).cast!!
            assertEquals("a1", cast.audio)
            assertEquals("de", cast.text)
        }

    @Test
    fun castOnOpenIsTakenOnce() =
        runTest {
            val h = TogetherHarness(this)
            val armed = ArmedCast(11, FILM.set, "kid", "Phone", playing = true)
            h.live.castOnOpen(armed)

            assertEquals(armed, h.live.takeCast())
            assertNull(h.live.takeCast())
        }
}
