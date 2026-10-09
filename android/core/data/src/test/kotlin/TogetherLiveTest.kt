package data

import data.settings.InMemoryLibrarySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.PlaybackChange
import model.together.joined
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherEdit
import uniffi.mediagram_core.TogetherMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding the live session, hearing edits, and re-reading what was missed,
 * against a channel that is [testing.FakeCore]'s map of message ids to
 * texts. The moves themselves are `TogetherSenderTest`'s.
 */
class TogetherLiveTest {
    @Test
    fun refreshAnswersTheNewestLiveSessionAndDeletesTheStaleOnes() =
        runTest {
            val h = TogetherHarness(this)
            h.put(948, TV_SESSION.copy(session = "s-old", playing = false, at = NOW - 4 * HOUR_MS))
            h.put(949, TV_SESSION)
            h.put(950, TV_SESSION.copy(session = "s-2", set = "film-2"))
            // Playing 300 s into a 100 s film: past its end and the two minutes after.
            h.put(951, TV_SESSION.copy(session = "s-3", pos = 300.0))

            val found = h.live.refresh()
            runCurrent()

            assertEquals(LiveSession(950, TV_SESSION.copy(session = "s-2", set = "film-2")), found)
            assertEquals(found, h.live.live.value)
            assertTrue(h.live.available.value)
            assertEquals(setOf(948, 951), h.core.togetherDeletes.toSet())
            assertEquals(setOf(949, 950), h.core.togetherMessages.keys)
        }

    @Test
    fun aFailedReadIsUnavailableAndKeepsWhatWasLive() =
        runTest {
            val h = TogetherHarness(this)
            h.put(950, TV_SESSION)
            val found = h.live.refresh()

            h.core.togetherFailure = IllegalStateException("offline")

            assertEquals(found, h.live.refresh())
            assertEquals(LiveSession(950, TV_SESSION), h.live.live.value)
            assertFalse(h.live.available.value)
        }

    @Test
    fun nobodySignedInOrNoLibraryChosenIsUnavailable() =
        runTest {
            val unsigned = TogetherHarness(this).apply { core.authorized = false }
            assertFalse(unsigned.live.available.value)
            unsigned.live.refresh()
            assertFalse(unsigned.live.available.value)

            val unchosen = TogetherHarness(this, settings = InMemoryLibrarySettings())
            unchosen.live.refresh()
            assertFalse(unchosen.live.available.value)
        }

    /** A push only arrives over a connection, so hearing one is reaching Telegram again. */
    @Test
    fun aPushAfterAFailedReadIsAvailableAgain() =
        runTest {
            val h = TogetherHarness(this)
            h.core.togetherFailure = IllegalStateException("offline")
            h.live.refresh()
            assertFalse(h.live.available.value)

            h.watch()
            h.push(950, TV_SESSION)

            assertTrue(h.live.available.value)
        }

    @Test
    fun aSessionHeardByPushAndPushedOutOfTheNewest100IsReadByIdSoNoSecondOneStarts() =
        runTest {
            val h = TogetherHarness(this)
            h.put(800, TV_SESSION)
            h.core.togetherOld += 800
            h.watch()
            h.push(800, TV_SESSION)

            val started = h.live.start(A, FILM)

            assertEquals(Started(LiveSession(800, TV_SESSION), conflict = true), started)
            assertEquals(setOf(800), h.core.togetherMessages.keys)
            assertEquals(listOf(800), h.core.togetherReads)
        }

    /** The read left before the TV paused; the pause heard meanwhile is the newer news, so it is what stays. */
    @Test
    fun aPushHeardWhileTheNewestAreReadIsNewerThanTheRead() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.put(800, TV_SESSION.copy(session = "s-800"))
            h.core.togetherOld += 800
            h.push(800, TV_SESSION.copy(session = "s-800"))
            h.put(950, TV_SESSION)
            val gate = CompletableDeferred<Unit>()
            h.core.togetherReadGate = gate // the read of 800 by id, after the newest are read

            val found = async { h.live.refresh() }
            runCurrent()
            val tvPause = TV_SESSION.copy(playing = false, pos = 30.0)
            h.push(950, tvPause)
            gate.complete(Unit)

            assertEquals(LiveSession(950, tvPause), found.await())
            assertEquals(LiveSession(950, tvPause), h.live.live.value)
        }

    @Test
    fun aRememberedIdThatIsNoSessionIsForgottenAfterOneRead() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(800, TV_SESSION)
            h.core.togetherMessages[800] = "an ordinary post"
            h.core.togetherOld += 800

            h.live.refresh()
            h.live.refresh()

            assertEquals(listOf(800), h.core.togetherReads)
            assertNull(h.live.live.value)
        }

    @Test
    fun anotherScreensEditIsNewsAMessageThatIsNoSessionChangesNothingAndAnEndClearsIt() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()

            h.push(950, TV_SESSION)
            assertEquals(LiveSession(950, TV_SESSION), h.live.live.value)

            h.push(TogetherMessage(951, "#mlib-together v=1\n{\"session\":\"s-x\"}"))
            assertEquals(LiveSession(950, TV_SESSION), h.live.live.value)

            h.push(950, TV_SESSION.copy(playing = false, ended = true))
            assertNull(h.live.live.value)
        }

    @Test
    fun aStaleSessionHeardByPushIsNotOffered() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(950, TV_SESSION)
            assertEquals(LiveSession(950, TV_SESSION), h.live.live.value)

            h.push(950, TV_SESSION.copy(playing = false, at = NOW - 4 * HOUR_MS))

            assertNull(h.live.live.value)
        }

    /** The core's heard slot keeps its message after an event; read again, it is older than what this device moved since. */
    @Test
    fun theHeardSlotReadAgainAfterAnOwnMoveDoesNotBringTheOlderSessionBack() =
        runTest {
            val h = TogetherHarness(this)
            h.put(950, TV_SESSION)
            h.watch()
            h.push(950, TV_SESSION)
            val mine = h.live.join(950, A)!!
            runCurrent()
            assertEquals(listOf(mine.session), h.edits)

            h.events.emit(LibraryEvent.TOGETHER)
            runCurrent()

            assertEquals(mine, h.live.live.value)
            assertEquals(joined(TV_SESSION, A), mine.session)
        }

    @Test
    fun listeningLastsOnlyWhileSomeoneWatches() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            assertEquals(0, h.events.subscriptionCount.value)

            val home = h.watch()
            val player = h.watch()
            assertEquals(1, h.events.subscriptionCount.value)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(listOf(id), h.core.togetherReads)

            home.cancel()
            runCurrent()
            assertEquals(1, h.events.subscriptionCount.value)

            player.cancel()
            runCurrent()
            assertEquals(0, h.events.subscriptionCount.value)
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(listOf(id), h.core.togetherReads)
        }

    @Test
    fun recheckReadsTheSessionsALocalScreenIsInAndTellsWhatChangedOrVanished() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.push(950, TV_SESSION)
            val paused = h.message(id).copy(playing = false, by = TV.screen, members = listOf(A, TV))
            h.put(id, paused)

            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(listOf(id), h.core.togetherReads)
            assertEquals(LiveSession(id, paused), h.live.live.value)

            h.core.togetherMessages.remove(id)
            advanceTimeBy(30_000)
            runCurrent()
            assertNull(h.live.live.value)
        }

    @Test
    fun aRecheckReadThatPredatesAnEditOnItsWayIsSetAside() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.put(id, h.message(id).copy(by = TV.screen, members = listOf(A, TV)))
            val gate = CompletableDeferred<Unit>()
            h.core.togetherReadGate = gate
            advanceTimeBy(30_000)
            runCurrent()

            val held = h.holdEdit()
            val paused = h.live.act(id, A.screen, PlaybackChange(playing = false, pos = 42.0))!!
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(LiveSession(id, paused), h.live.live.value)
            held.complete(TogetherEdit.Ok)
        }

    @Test
    fun aRecheckReadAcrossWhichAnOwnEditLandedIsSetAside() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            h.put(id, h.message(id).copy(by = TV.screen, members = listOf(A, TV)))
            val gate = CompletableDeferred<Unit>()
            h.core.togetherReadGate = gate
            advanceTimeBy(30_000)
            runCurrent()

            val paused = h.live.act(id, A.screen, PlaybackChange(playing = false, pos = 42.0))!!
            runCurrent()
            assertEquals(listOf(paused), h.edits)
            gate.complete(Unit)
            runCurrent()

            assertEquals(LiveSession(id, paused), h.live.live.value)
        }

    @Test
    fun aRecheckThatCannotReadTellsNothingAndTriesAgainOnTheNextTick() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            val id = h.start()
            val before = h.live.live.value
            h.core.togetherFailure = IllegalStateException("offline")

            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(before, h.live.live.value)

            h.core.togetherFailure = null
            val paused = h.message(id).copy(playing = false, by = TV.screen)
            h.put(id, paused)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(LiveSession(id, paused), h.live.live.value)
        }

    @Test
    fun aJoinAskedForOnOpenIsTakenOnce() =
        runTest {
            val h = TogetherHarness(this)
            assertNull(h.live.takeJoin())

            h.live.joinOnOpen(950)

            assertEquals(950, h.live.takeJoin())
            assertNull(h.live.takeJoin())
        }
}
