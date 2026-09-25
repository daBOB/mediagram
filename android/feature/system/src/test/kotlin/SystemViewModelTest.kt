package system

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import data.CoreClient
import data.CoreProvider
import data.RefreshLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.CacheOccupancy
import playback.CacheProvider
import playback.PlaybackCounters
import uniffi.mediagram_core.CatalogFacts
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val READ_FAILED = "System information could not be read. Try again."

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemViewModelTest {
    private lateinit var core: CoreClient

    @Before fun prepareIoBoundaries() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        core = mockk()
        coEvery { core.catalogFacts() } returns CatalogFacts("channel", 4u, 2u, 3u, 10)
        every { core.isAuthorized() } returns true
        mockkObject(CacheProvider)
        coEvery { CacheProvider.occupancy(any(), any()) } returns
            CacheOccupancy(heldBytes = 12, budgetBytes = 100, volumeLabel = "Internal storage", fellBack = false, capBytes = 100)
    }

    @After fun restoreIoBoundaries() {
        unmockkObject(CacheProvider)
        Dispatchers.resetMain()
    }

    @Test fun aCoreReadFailureDoesNotEscapeTheSnapshotCollector() =
        runTest {
            coEvery { core.catalogFacts() } throws IOException("private catalog path")
            val vm = model()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertNull(vm.state.value)
                assertEquals(READ_FAILED, vm.failure.value)
                vm.retry()
                runCurrent()
                assertEquals(READ_FAILED, vm.failure.value)
                coEvery { core.catalogFacts() } returns CatalogFacts("channel", 7u, 2u, 3u, 10)
                vm.retry()
                runCurrent()
                assertEquals(7L, assertNotNull(vm.state.value).sets)
                assertNull(vm.failure.value)
                coVerify(exactly = 3) { core.catalogFacts() }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun aCacheOpenFailureDoesNotEscapeTheSnapshotCollector() =
        runTest {
            coEvery { CacheProvider.occupancy(any(), any()) } throws IOException("private cache path")
            val vm = model()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertNull(vm.state.value)
                assertEquals(READ_FAILED, vm.failure.value)
                coEvery { CacheProvider.occupancy(any(), any()) } returns
                    CacheOccupancy(heldBytes = 36, budgetBytes = 200, volumeLabel = "Internal storage", fellBack = false, capBytes = 200)
                vm.retry()
                runCurrent()
                val current = assertNotNull(vm.state.value)
                assertEquals(36L, current.heldBytes)
                assertEquals(200L, current.budgetBytes)
                assertNull(vm.failure.value)
                coVerify(exactly = 2) { CacheProvider.occupancy(any(), any()) }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun aLaterReadFailureKeepsThePriorSnapshotUntilRetrySucceeds() =
        runTest {
            val vm = model()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                val previous = assertNotNull(vm.state.value)
                coEvery { core.catalogFacts() } throws IOException("private account details")
                vm.retry()
                runCurrent()
                assertSame(previous, vm.state.value)
                assertEquals(READ_FAILED, vm.failure.value)
                coEvery { core.catalogFacts() } returns CatalogFacts("channel", 8u, 2u, 3u, 10)
                vm.retry()
                runCurrent()
                assertEquals(8L, assertNotNull(vm.state.value).sets)
                assertNull(vm.failure.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test fun leavingCancelsTheSnapshotWithoutAnErrorAndReentryReadsAgain() =
        runTest {
            var cancelled = false
            coEvery { core.catalogFacts() } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val vm = model()
            try {
                val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                subscription.cancel()
                advanceTimeBy(5_001)
                runCurrent()
                assertTrue(cancelled)
                assertNull(vm.failure.value)
                coEvery { core.catalogFacts() } returns CatalogFacts("channel", 9u, 2u, 3u, 10)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertEquals(9L, assertNotNull(vm.state.value).sets)
                assertNull(vm.failure.value)
                coVerify(exactly = 2) { core.catalogFacts() }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private fun model(): SystemViewModel {
        val provider = mockk<CoreProvider>()
        coEvery { provider.awaitCore() } returns core
        return SystemViewModel(ApplicationProvider.getApplicationContext<Context>(), provider, PlaybackCounters(), RefreshLog())
    }
}
