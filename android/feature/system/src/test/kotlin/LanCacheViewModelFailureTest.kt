package system

import androidx.lifecycle.viewModelScope
import data.settings.LanCacheTokenSettings
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
import java.security.GeneralSecurityException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FAILURE = "Home cache server settings could not be read or saved. Try again."

/** The encrypted store's failure once its key is gone: a GeneralSecurityException from every read or write. */
private class KeystoreTokenSettings(
    var readable: Boolean,
    var writable: Boolean,
) : LanCacheTokenSettings {
    private var stored: String? = null

    override suspend fun read(): String? = if (readable) stored else throw GeneralSecurityException("key invalidated")

    override suspend fun write(token: String) {
        if (!writable) throw GeneralSecurityException("key invalidated")
        stored = token
    }

    override suspend fun clear() {
        stored = null
    }
}

/** A token store that cannot be read or written says so in Settings instead of taking Settings down. */
@RunWith(RobolectricTestRunner::class)
class LanCacheViewModelFailureTest {
    @Before
    fun prepare() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restore() = Dispatchers.resetMain()

    @Test
    fun anUnreadableTokenStoreIsSaidAndOpeningAgainRecovers() =
        runTest {
            val store = KeystoreTokenSettings(readable = false, writable = true)
            val vm = testLanCacheViewModel(tokenSettings = store)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertNull(vm.state.value)
                assertEquals(FAILURE, vm.failure.value)

                store.readable = true
                vm.open()
                runCurrent()

                assertNotNull(vm.state.value)
                assertNull(vm.failure.value)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun aTokenTheStoreCannotSaveIsSaidUntilASaveLands() =
        runTest {
            val store = KeystoreTokenSettings(readable = true, writable = false)
            val vm = testLanCacheViewModel(tokenSettings = store)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                assertTrue(vm.saveToken("a".repeat(64)), "the token itself was well formed")
                runCurrent()
                // The save re-reads the block, and that read working does not unsay the save.
                assertEquals(FAILURE, vm.failure.value)
                assertEquals(false, assertNotNull(vm.state.value).hasToken)

                store.writable = true
                vm.saveToken("a".repeat(64))
                runCurrent()

                assertNull(vm.failure.value)
                assertEquals(true, assertNotNull(vm.state.value).hasToken)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
}
