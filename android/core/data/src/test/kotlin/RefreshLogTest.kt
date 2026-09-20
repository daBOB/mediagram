package data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a refresh did, as the System screen will read it back. The refresh
 * itself is what writes here, so these go through the repository rather
 * than calling [RefreshLog.record] directly — a log that recorded what a
 * test told it to would pin nothing about the app.
 */
class RefreshLogTest {

    private fun repositoryOver(core: FakeCore, log: RefreshLog) =
        DefaultCatalogRepository(ResolvedCoreProvider(core), settingsWithAChosenLibrary(), log)

    /** A refresh that installs a newer snapshot is the case worth reporting. */
    @Test
    fun aNewerSnapshotReadsAsUpdated() = runTest {
        val core = FakeCore(publishedAt = listOf(1_758_300_000L, 1_758_900_000L))
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.Updated, log.last())
    }

    /**
     * Asking again when nothing has been pushed is the ordinary case, and it
     * is not a failure — the library is current, which is what was wanted.
     */
    @Test
    fun anUnchangedSnapshotReadsAsAlreadyCurrent() = runTest {
        val core = FakeCore(publishedAt = listOf(1_758_300_000L))
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.AlreadyCurrent, log.last())
    }

    /**
     * The first refresh a device ever makes finds nothing installed and
     * leaves a whole library behind it. Nothing to something is the largest
     * update there is, not an absent reading.
     */
    @Test
    fun theFirstLibraryEverInstalledReadsAsUpdated() = runTest {
        val core = FakeCore(publishedAt = listOf(null, 1_758_900_000L))
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.Updated, log.last())
    }

    /** A refusal keeps the sentence the core wrote, which says what to do about it. */
    @Test
    fun aFailedRefreshKeepsTheCoresOwnSentence() = runTest {
        val core = FakeCore(refreshFails = "the channel could not be reached")
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.Refused("the channel could not be reached"), log.last())
    }

    /** Before anything has been asked, there is nothing to report. */
    @Test
    fun anUntouchedLogHasNothingToSay() {
        assertNull(RefreshLog().last())
    }
}
