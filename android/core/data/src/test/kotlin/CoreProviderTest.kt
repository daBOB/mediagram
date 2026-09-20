package data

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"

class CoreProviderTest {

    @Test
    fun withNoStoredIdentityThereIsNoCore() = runTest {
        val provider = StoredCoreProvider(InMemoryTelegramSettings()) { FakeCore() }
        assertNull(provider.coreOrNull())
    }

    @Test
    fun oneCoreIsBuiltAndThereafterHandedOutAgain() = runTest {
        var builds = 0
        val settings = InMemoryTelegramSettings()
        settings.write(1234, WELL_FORMED_HASH)
        val provider = StoredCoreProvider(settings) {
            builds++
            FakeCore()
        }

        val first = provider.coreOrNull()
        val second = provider.awaitCore()

        assertSame(first, second)
        assertEquals(1, builds, "the core opens one data directory; a second over the same one is a defect")
    }

    @Test
    fun awaitingHandsBackNothingUntilAnIdentityIsSupplied() = runTest {
        val provider = StoredCoreProvider(InMemoryTelegramSettings()) { FakeCore() }

        val waiting = async { provider.awaitCore() }
        runCurrent()
        assertFalse(waiting.isCompleted, "there is no core to hand out before an identity is stored")

        provider.supply(1234, WELL_FORMED_HASH)

        assertSame(provider.coreOrNull(), waiting.await())
    }

    @Test
    fun forgettingDropsTheIdentityAndTheCoreBuiltFromIt() = runTest {
        val settings = InMemoryTelegramSettings()
        val provider = StoredCoreProvider(settings) { FakeCore() }
        provider.supply(1234, WELL_FORMED_HASH)

        provider.forget()

        assertNull(settings.read())
        assertNull(provider.coreOrNull())
    }

    @Test
    fun theCoreAfterAResetIsBuiltFromTheIdentityTypedInAfterIt() = runTest {
        val identities = mutableListOf<Int>()
        val settings = InMemoryTelegramSettings()
        val provider = StoredCoreProvider(settings) {
            identities += it.apiId
            FakeCore()
        }

        provider.supply(1234, WELL_FORMED_HASH)
        provider.forget()
        provider.supply(5678, WELL_FORMED_HASH)

        assertEquals(listOf(1234, 5678), identities)
    }
}
