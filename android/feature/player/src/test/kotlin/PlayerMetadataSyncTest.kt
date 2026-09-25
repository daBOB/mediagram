package player

import androidx.media3.common.Player
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers [syncMetadataToHandle] — [PlayerViewModel.openSet] resolving
 * pushes the same title line and poster onto [PlayerHandle.setMetadata]
 * that `PlaybackService`'s `MediaSession` reads from. [FakePlayerHandle]
 * only records a [FakePlayerHandle.setMetadata] call once a player is
 * installed — matching `DefaultPlayerHandle`'s own early return with no
 * current item — so every test here installs one first.
 */
class PlayerMetadataSyncTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aResolvedSetPublishesItsTitleLineToTheHandle() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply { installPlayer(mockk<Player>(relaxed = true)) }
        val film = fakeMediaSet(setId = "s1", title = "Blade Runner 2049")
        val vm = buildViewModel(handle, catalogRepository = FakeCatalogRepository(mapOf("s1" to film)))

        vm.open("s1")
        advanceUntilIdle()

        assertEquals("Blade Runner 2049", handle.lastMetadata?.title?.toString())
    }

    @Test
    fun stoppingSettlesBackToBlankMetadata() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle().apply { installPlayer(mockk<Player>(relaxed = true)) }
        val film = fakeMediaSet(setId = "s1", title = "Blade Runner 2049")
        val vm = buildViewModel(handle, catalogRepository = FakeCatalogRepository(mapOf("s1" to film)))
        vm.open("s1")
        advanceUntilIdle()

        vm.stop()
        advanceUntilIdle()

        assertEquals("", handle.lastMetadata?.title?.toString() ?: "")
        assertNull(handle.lastMetadata?.artworkUri)
    }
}
