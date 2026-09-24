package player

import androidx.lifecycle.viewModelScope
import data.WatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.ListOfSets
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerActionNoticeTest {
    @After fun reset() = Dispatchers.resetMain()

    @Test fun retryUsesTheAcknowledgedSnapshotAndClearsOnlyItsOwnNotice() =
        runTest {
            installMainDispatcher()
            val acknowledged = FakeWatchStateRepository()
            var failing = true
            val repository =
                object : WatchStateRepository by acknowledged {
                    override suspend fun setWatchlisted(
                        setId: String,
                        listed: Boolean,
                    ) {
                        if (failing) error("unreadable snapshot")
                        acknowledged.setWatchlisted(setId, listed)
                    }
                }
            val vm = actionViewModel(FakePlayerHandle(), repository)
            try {
                vm.open("s1")
                vm.toggleWatchlist()
                runCurrent()
                val notice = assertNotNull(vm.actionNotice.value)
                vm.toggleKids()
                runCurrent()
                assertEquals(notice, vm.actionNotice.value)
                failing = false
                vm.toggleWatchlist()
                runCurrent()
                assertEquals(listOf("s1"), repository.snapshot.value.watchlist)
                assertNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun refusedCreationDoesNotAttemptMembershipAndCanBeRetried() =
        runTest {
            installMainDispatcher()
            val acknowledged = FakeWatchStateRepository()
            var refused = true
            val repository =
                object : WatchStateRepository by acknowledged {
                    override suspend fun createList(name: String): ListOfSets? = if (refused) null else acknowledged.createList(name)
                }
            val vm = actionViewModel(FakePlayerHandle(), repository)
            try {
                vm.open("s1")
                vm.createListAndAdd("Favourites")
                runCurrent()
                assertNotNull(vm.actionNotice.value)
                assertTrue(acknowledged.calls.isEmpty())
                refused = false
                vm.createListAndAdd("Favourites")
                runCurrent()
                assertEquals(
                    listOf("s1"),
                    repository.snapshot.value.collections
                        .single()
                        .items,
                )
                assertNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun refusedMembershipKeepsTheSuccessfullyCreatedListWithoutClaimingItsItemWasSaved() =
        runTest {
            installMainDispatcher()
            val acknowledged = FakeWatchStateRepository()
            val repository =
                object : WatchStateRepository by acknowledged {
                    override suspend fun setInList(
                        id: String,
                        setId: String,
                        included: Boolean,
                    ) = false
                }
            val vm = actionViewModel(FakePlayerHandle(), repository)
            try {
                vm.open("s1")
                vm.createListAndAdd("Favourites")
                runCurrent()
                assertEquals(
                    ListOfSets("list-1", "Favourites", emptyList()),
                    repository.snapshot.value.collections
                        .single(),
                )
                assertNotNull(vm.actionNotice.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun aCommittedWriteWhoseReloadFailsDoesNotInventASnapshotOrRetryAutomatically() =
        runTest {
            installMainDispatcher()
            val acknowledged = FakeWatchStateRepository()
            var commits = 0
            val repository =
                object : WatchStateRepository by acknowledged {
                    override suspend fun setWatchlisted(
                        setId: String,
                        listed: Boolean,
                    ) {
                        commits++
                        error("snapshot reload failed after commit")
                    }
                }
            val vm = actionViewModel(FakePlayerHandle(), repository)
            try {
                vm.open("s1")
                vm.toggleWatchlist()
                runCurrent()
                assertEquals(1, commits)
                assertTrue(
                    repository.snapshot.value.watchlist
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
            val repository =
                object : WatchStateRepository by FakeWatchStateRepository() {
                    override suspend fun setKids(
                        setId: String,
                        marked: Boolean,
                    ) {
                        finish.await()
                        error("old title's write")
                    }
                }
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, repository)
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
