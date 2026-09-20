package rust

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.mediagram_core.Core

/**
 * The first point where the native library actually loads on a device: a
 * missing ABI slice or a bad JNA classifier surfaces here as
 * [UnsatisfiedLinkError] or [ClassNotFoundException], not as a Kotlin
 * compile error, so this only runs on real hardware or an emulator.
 */
@RunWith(AndroidJUnit4::class)
class CoreLoadsTest {

    @Test
    fun theNativeLibraryLoadsAndReportsNoSession() {
        val dir = ApplicationProvider.getApplicationContext<Context>().filesDir
        val core = Core(dir.absolutePath, apiId = 0, apiHash = "test-only-dummy-hash")

        assertFalse(core.isAuthorized())
    }
}
