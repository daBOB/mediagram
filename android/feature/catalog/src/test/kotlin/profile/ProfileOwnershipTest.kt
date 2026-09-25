package catalog.profile

import catalog.CatalogCore
import catalog.CatalogCoreProvider
import catalog.MainDispatcherRule
import data.CoreClient
import data.DefaultWatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.Profile
import org.junit.Rule
import uniffi.mediagram_core.StateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

private class ProfileCore : CoreClient by CatalogCore() {
    var chosen = "a"
    var pauseNext = false
    var failPaused = false
    val started = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()

    override suspend fun profiles() = listOf(uniffi.mediagram_core.Profile("a", "Alice"), uniffi.mediagram_core.Profile("b", "Bea"))

    override suspend fun chosenProfile() = chosen

    override suspend fun chooseProfile(id: String): Boolean {
        chosen = id
        return true
    }

    override suspend fun snapshot(profileId: String): StateSnapshot {
        if (pauseNext) {
            pauseNext = false
            started.complete(Unit)
            finish.await()
            if (failPaused) error("old snapshot failed")
        }
        return StateSnapshot(emptyList(), emptyList(), listOf(profileId), emptyList(), emptyList(), null)
    }
}

private object SettledSync : WatchSync {
    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() = Unit

    override suspend fun awaitFirstRound() = Unit
}

class ProfileOwnershipTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun aLateSuccessfulChoiceCannotReplaceTheNewerChoiceOnScreen() = lateChoice(fail = false, returnToFirst = false)

    @Test fun aLateFailedChoiceCannotReplaceTheNewerChoiceWithAnError() = lateChoice(fail = true, returnToFirst = false)

    @Test fun aLateFailureCannotReplaceANewerChoiceOfTheSameProfile() = lateChoice(fail = true, returnToFirst = true)

    @Test
    fun aLateReloadErrorCannotReplaceANewerChoice() =
        runTest {
            val core = ProfileCore()
            val repository = DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined)
            val vm = ProfileViewModel(repository, SettledSync)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            runCurrent()
            core.pauseNext = true
            core.failPaused = true
            vm.retry()
            try {
                core.started.await()
                vm.choose("b")
                runCurrent()
            } finally {
                core.finish.complete(Unit)
                runCurrent()
            }
            assertEquals(ProfileUiState.Chosen(Profile("b", "Bea")), vm.state.value)
            assertEquals("b", repository.chosenProfileId.value)
        }

    private fun lateChoice(
        fail: Boolean,
        returnToFirst: Boolean,
    ) = runTest {
        val core = ProfileCore()
        val repository = DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined)
        val vm = ProfileViewModel(repository, SettledSync)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        assertEquals(ProfileUiState.Chosen(Profile("a", "Alice")), vm.state.value)
        core.pauseNext = true
        core.failPaused = fail
        vm.choose("a")
        try {
            core.started.await()
            vm.choose("b")
            if (returnToFirst) vm.choose("a")
            runCurrent()
        } finally {
            core.finish.complete(Unit)
            runCurrent()
        }
        val expected = if (returnToFirst) Profile("a", "Alice") else Profile("b", "Bea")
        assertEquals(expected.id, repository.chosenProfileId.value)
        assertEquals(listOf(expected.id), repository.snapshot.value.watchlist)
        assertEquals(ProfileUiState.Chosen(expected), vm.state.value)
    }
}
