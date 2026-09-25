package system

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import playback.LanCacheTokenStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Token and manual-address save validation — split from [LanCacheViewModelTest] to keep both files under the line limit. */
@RunWith(RobolectricTestRunner::class)
class LanCacheViewModelValidationTest {
    @Before
    fun prepare() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restore() = Dispatchers.resetMain()

    @Test
    fun savingATokenClearsAPriorRejection() =
        runTest {
            val tokenStatus = LanCacheTokenStatus().apply { markRejected() }
            val vm = testLanCacheViewModel(tokenStatus = tokenStatus)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertEquals(true, assertNotNull(vm.state.value).tokenRejected)

                vm.saveToken("a".repeat(64))
                runCurrent()

                assertFalse(assertNotNull(vm.state.value).tokenRejected)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun anInvalidTokenIsRefusedWithASentenceAndNeverReachesTheStore() =
        runTest {
            val vm = testLanCacheViewModel()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                vm.saveToken("not a real token")
                runCurrent()

                val state = assertNotNull(vm.state.value)
                assertNotNull(state.tokenError)
                assertEquals(false, state.hasToken)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun aValidTokenClearsAPriorTokenError() =
        runTest {
            val vm = testLanCacheViewModel()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                vm.saveToken("nope")
                runCurrent()
                assertNotNull(assertNotNull(vm.state.value).tokenError)

                vm.saveToken("a".repeat(64))
                runCurrent()

                assertNull(assertNotNull(vm.state.value).tokenError)
                assertEquals(true, assertNotNull(vm.state.value).hasToken)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun aSchemeLessManualAddressIsAcceptedAndNormalized() =
        runTest {
            val locator = FakeLocator()
            val vm = testLanCacheViewModel(locator = locator)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                vm.setManualAddress("192.168.1.9:7788")
                runCurrent()

                val state = assertNotNull(vm.state.value)
                assertNull(state.addressError)
                assertEquals("http://192.168.1.9:7788", state.manualAddress)
                assertEquals(1, locator.discoverCalls, "a saved address is worth a fresh discovery pass")
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun anUnparseableManualAddressIsRefusedWithASentenceAndNeverSaved() =
        runTest {
            val locator = FakeLocator()
            val vm = testLanCacheViewModel(locator = locator)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                vm.setManualAddress("htp://bad-scheme")
                runCurrent()

                val state = assertNotNull(vm.state.value)
                assertNotNull(state.addressError)
                assertEquals("", state.manualAddress)
                assertEquals(0, locator.discoverCalls, "a refused address is never worth a discovery pass")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
}
