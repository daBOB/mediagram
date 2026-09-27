package rust

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import testing.CoreContract
import uniffi.mediagram_core.Core
import java.io.File
import java.util.UUID

/**
 * [CoreContract] against the real generated `Core`, built the way
 * `app/.../di/CoreModule.kt` builds one — on the tablet, on a fresh data
 * directory with no Telegram session, same as [CoreLoadsTest] next to it.
 *
 * A UUID subdirectory per test class instance (JUnit4 makes a fresh one per
 * `@Test` method) is what keeps the profile-mutating cases from seeing each
 * other's writes; a shared directory would make "a created profile is
 * listed" depend on test order.
 */
@RunWith(AndroidJUnit4::class)
class RealCoreContractTest : CoreContract() {
    private lateinit var built: Core

    @Before
    fun buildCore() {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "contract-${UUID.randomUUID()}")
        built = Core(dir.absolutePath, apiId = 0, apiHash = "test-only-dummy-hash", deviceName = "core-contract-test")
    }

    @After
    fun closeCore() {
        built.retireLocalState()
        built.close()
    }

    override fun core(): Core = built
}
