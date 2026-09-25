package system

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.robolectric.Shadows
import playback.InMemoryLanCacheSettings
import playback.LanCacheTokenStatus
import playback.LanChunkProtocol
import playback.LanPutResult
import playback.LanServer
import playback.LanServerSource
import playback.LanServerStatus
import settings.InMemoryLanCacheTokenSettings
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Shared with [LanCacheViewModelValidationTest]. */
internal class FakeLocator : LanServerSource {
    private val _server = MutableStateFlow<LanServer?>(null)
    override val server: StateFlow<LanServer?> = _server
    private val _searching = MutableStateFlow(false)
    override val searching: StateFlow<Boolean> = _searching
    var discoverCalls = 0

    override fun discover() {
        discoverCalls++
    }

    fun setServer(value: LanServer?) {
        _server.value = value
    }

    fun setSearching(value: Boolean) {
        _searching.value = value
    }
}

internal class FakeClient(private val heldBytes: Long = 1_000_000) : LanChunkProtocol {
    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? = null

    override suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ) = LanPutResult.Stored

    override suspend fun verify(baseUrl: String): Boolean = true

    override suspend fun status(baseUrl: String): LanServerStatus? = LanServerStatus(heldBytes, 10_000_000, 1)
}

internal fun testLanCacheViewModel(
    locator: FakeLocator = FakeLocator(),
    client: LanChunkProtocol = FakeClient(),
    settings: InMemoryLanCacheSettings = InMemoryLanCacheSettings(),
    tokenSettings: InMemoryLanCacheTokenSettings = InMemoryLanCacheTokenSettings(),
    tokenStatus: LanCacheTokenStatus = LanCacheTokenStatus(),
) = LanCacheViewModel(
    ApplicationProvider.getApplicationContext(),
    settings,
    tokenSettings,
    tokenStatus,
    locator,
    client,
)

@RunWith(RobolectricTestRunner::class)
class LanCacheViewModelTest {
    @Before
    fun prepare() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restore() = Dispatchers.resetMain()

    private fun viewModel(
        locator: FakeLocator = FakeLocator(),
        client: LanChunkProtocol = FakeClient(),
        tokenStatus: LanCacheTokenStatus = LanCacheTokenStatus(),
    ) = testLanCacheViewModel(locator = locator, client = client, tokenStatus = tokenStatus)

    /**
     * Robolectric has no shadow for API 37 (this project's version), so
     * this runs at whatever lower SDK the test target defaults to — which
     * stands in for the real device this fix targets, a tablet on API 36.
     * `localNetworkPermissionGranted`'s own unit test in
     * `LanCacheInputTest` is what actually proves the >=37 branch.
     */
    @Test
    fun belowSdkThirtySevenNoPermissionIsEverNeeded() =
        runTest {
            val vm = viewModel()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                assertEquals(LanCacheConnection.NOT_FOUND, assertNotNull(vm.state.value).connection)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun withPermissionAndNoServerAndSearchingTheConnectionIsSearching() =
        runTest {
            grantLocalNetworkPermission()
            val locator = FakeLocator().apply { setSearching(true) }
            val vm = viewModel(locator = locator)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                assertEquals(LanCacheConnection.SEARCHING, assertNotNull(vm.state.value).connection)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun withPermissionAndNoServerAndNotSearchingTheConnectionIsNotFound() =
        runTest {
            grantLocalNetworkPermission()
            val vm = viewModel()
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                assertEquals(LanCacheConnection.NOT_FOUND, assertNotNull(vm.state.value).connection)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun aVerifiedServerIsConnectedAndReportsWhatItHolds() =
        runTest {
            grantLocalNetworkPermission()
            val locator = FakeLocator().apply { setServer(LanServer("http://10.0.0.5:7788", "10.0.0.5:7788")) }
            val vm = viewModel(locator = locator, client = FakeClient(heldBytes = 5_000_000))
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()

                val state = assertNotNull(vm.state.value)
                assertEquals(LanCacheConnection.CONNECTED, state.connection)
                assertEquals("10.0.0.5:7788", state.connectedHost)
                assertEquals(5_000_000L, state.heldBytes)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun openTriggersADiscoveryPass() =
        runTest {
            grantLocalNetworkPermission()
            val locator = FakeLocator()
            val vm = viewModel(locator = locator)
            try {
                vm.open()
                runCurrent()

                assertEquals(1, locator.discoverCalls)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private fun grantLocalNetworkPermission() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(app as android.app.Application).grantPermissions("android.permission.ACCESS_LOCAL_NETWORK")
    }
}
