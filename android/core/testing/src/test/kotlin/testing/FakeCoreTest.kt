package testing

import kotlinx.coroutines.test.runTest
import uniffi.mediagram_core.CoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * [FakeCore]'s own contract cases [CoreContract] cannot reach: that shared
 * suite only ever reads an *unknown* set, offline-safe against the real
 * core too. A mutation that dropped only the unknown-set check would still
 * pass it, because a positively-sized, known set's own end is a different
 * branch — this pins that one directly against the fake.
 *
 * The two clock-stepped-backward cases below pin the fake's own `MAX(…+1)`
 * clamps — the same clamps `crates/mediagram-core/src/state/rows.rs`'s
 * `set_watched` applies on conflict. They cannot run against the real core
 * offline: its clock is wall time, which nothing here can step backward.
 */
class FakeCoreTest {
    @Test
    fun readingPastAKnownSetsEndIsNotFound() =
        runTest {
            val core = FakeCore(totalSize = 10)

            assertFailsWith<CoreException.NotFound> { core.read("known", 10uL, 1u) }
        }

    @Test
    fun reFinishingClampsAheadOfARemovalEvenWhenTheClockStepsBack() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    clock = { 100L }
                }
            core.setWatched("p1", "01A", true) // finishedAt = 100
            core.clock = { 200L }
            core.setWatched("p1", "01A", false) // removedAt = max(200, 100+1) = 200
            core.clock = { 50L } // the clock steps backward

            core.setWatched("p1", "01A", true)

            // max(50, removedAt(200)+1) = 201, not 50 — a clock correction
            // must never re-date a finish behind its own removal.
            assertEquals(201L, core.snapshot("p1").watched.single().finishedAt)
        }

    @Test
    fun unmarkingClampsAheadOfItsFinishEvenWhenTheClockStepsBack() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    clock = { 100L }
                }
            core.setWatched("p1", "01A", true) // finishedAt = 100
            core.clock = { 50L } // the clock steps backward
            core.setWatched("p1", "01A", false) // removedAt = max(50, 100+1) = 101, not 50
            core.clock = { 60L }

            core.setWatched("p1", "01A", true)

            // Only visible indirectly: a re-finish clamps against whatever
            // removedAt actually holds. max(60, removedAt+1) is 102 with the
            // clamp above correctly applied, or 60 if it were dropped.
            assertEquals(102L, core.snapshot("p1").watched.single().finishedAt)
        }
}
