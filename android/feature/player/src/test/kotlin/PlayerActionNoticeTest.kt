package player

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import testing.WatchStateFixture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the player says about a mark write it could not confirm, over the
 * real repository: failures are injected where the app's own come from —
 * the provider that hands the core out — and refusals are the core's own.
 */
class PlayerActionNoticeTest {
    private val watch = WatchStateFixture()

    /** How many writes reached the provider, failed ones included. */
    private var writes = 0

    @After fun reset() = Dispatchers.resetMain()

    @Test fun retryUsesTheAcknowledgedSnapshotAndClearsOnlyItsOwnNotice() =
        runTest {
            installMainDispatcher()
            watch.provider.beforeCore = { if (++writes == 1) error("unreadable snapshot") }
            val vm = actionViewModel(FakePlayerHandle(), watch.repository)
            try {
                vm.open("s1")
                vm.toggleWatchlist()
                runCurrent()
                val notice = assertNotNull(vm.actionNotice.value)
                vm.toggleKids()
                runCurrent()
                assertEquals(notice, vm.actionNotice.value)
                vm.toggleWatchlist()
                runCurrent()
                assertEquals(listOf("s1"), watch.repository.snapshot.value.watchlist)
                assertNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /** The core refuses a list whose name is only whitespace; the dialog never sends one, but the refusal path is the same. */
    @Test fun refusedCreationDoesNotAttemptMembershipAndCanBeRetried() =
        runTest {
            installMainDispatcher()
            watch.provider.beforeCore = { writes++ }
            val vm = actionViewModel(FakePlayerHandle(), watch.repository)
            try {
                vm.open("s1")
                vm.createListAndAdd("   ")
                runCurrent()
                assertNotNull(vm.actionNotice.value)
                assertEquals(1, writes, "a refused creation is not followed by a membership write")
                assertTrue(watch.repository.snapshot.value.collections.isEmpty())
                vm.createListAndAdd("Favourites")
                runCurrent()
                assertEquals(
                    listOf("s1"),
                    watch.repository.snapshot.value.collections
                        .single()
                        .items,
                )
                assertNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /**
     * The new list is removed elsewhere — another screen, or a sync round —
     * between its creation and the title being filed on it, so the core
     * refuses the membership.
     */
    @Test fun refusedMembershipKeepsTheSuccessfullyCreatedListWithoutClaimingItsItemWasSaved() =
        runTest {
            installMainDispatcher()
            watch.provider.beforeCore = {
                if (++writes == 2) {
                    val created = watch.core.snapshot(WatchStateFixture.VIEWER.id).collections.single()
                    watch.core.deleteCollection(WatchStateFixture.VIEWER.id, created.id)
                }
            }
            val vm = actionViewModel(FakePlayerHandle(), watch.repository)
            try {
                vm.open("s1")
                vm.createListAndAdd("Favourites")
                runCurrent()
                val list =
                    watch.repository.snapshot.value.collections
                        .single()
                assertEquals("Favourites" to emptyList(), list.name to list.items)
                assertNotNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun aFailedWriteIsTriedOnceAndInventsNoSnapshot() =
        runTest {
            installMainDispatcher()
            watch.provider.beforeCore = {
                writes++
                error("keystore unavailable")
            }
            val vm = actionViewModel(FakePlayerHandle(), watch.repository)
            try {
                vm.open("s1")
                vm.toggleWatchlist()
                runCurrent()
                assertEquals(1, writes, "a failed write is not retried behind the viewer's back")
                assertTrue(
                    watch.repository.snapshot.value.watchlist
                        .isEmpty(),
                )
                assertTrue(assertNotNull(vm.actionNotice.value).contains("confirm"))
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun aLateFailureDoesNotAppearAfterLeavingAndReopeningTheSameTitle() =
        runTest {
            installMainDispatcher()
            val finish = CompletableDeferred<Unit>()
            watch.provider.beforeCore = {
                if (++writes == 1) {
                    finish.await()
                    error("old title's write")
                }
            }
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, watch.repository)
            try {
                vm.open("s1")
                vm.toggleKids()
                vm.open("s2")
                vm.open("s1")
                finish.complete(Unit)
                runCurrent()
                assertNull(vm.actionNotice.value)
                assertFalse(handle.stopCalled)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
}
