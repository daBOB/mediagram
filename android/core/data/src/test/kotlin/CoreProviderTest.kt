package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import settings.InMemoryTelegramSettings
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"

/** Delegates every dispatch to [delegate], recording the thread each one actually ran on. */
private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    @Volatile
    var lastDispatchThread: Thread? = null
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context) {
            lastDispatchThread = Thread.currentThread()
            block.run()
        }
    }
}

class CoreProviderTest {

    private val probeExecutor = Executors.newSingleThreadExecutor()

    @After
    fun shutDownTheProbeThread() {
        probeExecutor.shutdown()
    }

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

    /**
     * Dropping the reference is not enough. The core holds a live,
     * authorised connection to Telegram; deleting the auth key file on disk
     * does nothing to one that is already open, and anything still holding
     * the old core — the player's data source factory, for one — would go
     * on fetching bytes as the account the person was told had been signed
     * out.
     */
    @Test
    fun forgettingClosesTheCoreRatherThanOnlyLettingGoOfIt() = runTest {
        val discarded = FakeCore()
        val provider = StoredCoreProvider(InMemoryTelegramSettings()) { discarded }
        provider.supply(1234, WELL_FORMED_HASH)

        provider.forget()

        assertTrue(discarded.closed, "an open Telegram connection outlives the auth key file")
    }

    @Test
    fun theCurrentCoreIsVisibleWithoutSuspendingAndChangesWhenItIsReplaced() = runTest {
        val cores = ArrayDeque(listOf(FakeCore(), FakeCore()))
        val provider = StoredCoreProvider(InMemoryTelegramSettings()) { cores.removeFirst() }

        assertNull(provider.core.value)
        provider.supply(1234, WELL_FORMED_HASH)
        val first = provider.core.value
        assertNotNull(first)

        provider.forget()
        assertNull(provider.core.value, "between signing out and setting up again there is no core to read through")

        provider.supply(5678, WELL_FORMED_HASH)
        assertNotEquals(first, provider.core.value)
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

    /**
     * An identity written before the core it produces is proven to build
     * turns one bad entry into a launch crash loop: every later launch
     * reads it back, fails the same way, and never reaches a screen that
     * could clear it.
     */
    @Test
    fun anIdentityThatCannotBuildACoreIsNotStored() = runTest {
        val settings = InMemoryTelegramSettings()
        val provider = StoredCoreProvider(settings) { error("the native core would not load") }

        assertFailsWith<IllegalStateException> { provider.supply(1234, WELL_FORMED_HASH) }

        assertNull(settings.read())
    }

    /**
     * The first build loads the native library, decrypts the stored
     * identity through the keystore and stats the auth key file. Both
     * callers reach this from the main dispatcher.
     */
    @Test
    fun constructionRunsOnTheGivenDispatcherNotTheCallingThread() = runTest {
        val callingThread = Thread.currentThread()
        val recording = RecordingDispatcher(probeExecutor.asCoroutineDispatcher())
        val settings = InMemoryTelegramSettings()
        settings.write(1234, WELL_FORMED_HASH)

        StoredCoreProvider(settings, recording) { FakeCore() }.coreOrNull()

        // Both halves matter: a dispatcher that's never actually invoked
        // would leave lastDispatchThread null, which is also "not equal
        // to callingThread" but proves nothing.
        assertNotNull(recording.lastDispatchThread, "the given dispatcher was never actually used")
        assertNotEquals(callingThread, recording.lastDispatchThread)
    }
}
