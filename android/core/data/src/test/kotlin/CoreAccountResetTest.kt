package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
import testing.FakeCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreAccountResetTest {
    @Test
    fun resetClosesBeforeClearingAndExcludesReopeningUntilClearFinishes() =
        runTest {
            val first = FakeCore()
            val second = FakeCore()
            val settings = InMemoryTelegramSettings()
            settings.write(1234, "hash")
            val clients = ArrayDeque(listOf(first, second))
            val provider = StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) { clients.removeFirst() }
            assertSame(first, provider.awaitCore())
            val clearing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reset =
                launch {
                    provider.resetAccount(
                        object : CoreStorage {
                            override suspend fun clear() {
                                assertTrue(first.closed)
                                assertNull(provider.core.value)
                                clearing.complete(Unit)
                                release.await()
                            }
                        },
                    )
                }
            clearing.await()
            val reopened = async { provider.coreOrNull() }
            try {
                runCurrent()
                assertFalse(reopened.isCompleted)
                assertEquals(1, clients.size)
            } finally {
                release.complete(Unit)
            }
            reset.join()
            assertSame(second, reopened.await())
            assertEquals(1234, settings.read()?.apiId)
        }

    @Test
    fun aFailedCloseIsOwnedUntilRetryAndDoesNotPermitDeletionOrReopening() =
        runTest {
            var builds = 0
            val first = FakeCore(closeFailure = IllegalStateException("close refused"))
            val settings = InMemoryTelegramSettings()
            settings.write(1234, "hash")
            val provider =
                StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) {
                    builds++
                    first
                }
            provider.awaitCore()
            val storage = InMemoryCoreStorage()
            assertFailsWith<IllegalStateException> { provider.resetAccount(storage) }
            assertFalse(storage.cleared)
            assertNull(provider.core.value)
            assertFailsWith<IllegalStateException> { provider.coreOrNull() }
            assertEquals(1, builds)
            assertEquals(2, first.closeCalls)
            first.closeFailure = null
            provider.resetAccount(storage)
            assertEquals(3, first.closeCalls)
            assertTrue(storage.cleared)
            assertEquals(1234, settings.read()?.apiId)
        }

    /**
     * A retry after `close()` alone fails must not repeat a
     * `retireLocalState()` that already succeeded: the real generated `Core`
     * refuses every call once destroyed, so a second `retireLocalState()` on
     * a core `close()` had already flipped would throw too, forever.
     */
    @Test
    fun aRetryAfterAFailedCloseDoesNotRepeatAnAlreadySuccessfulRetirement() =
        runTest {
            val first = FakeCore(closeFailure = IllegalStateException("close refused"))
            val settings = InMemoryTelegramSettings()
            settings.write(1234, "hash")
            val provider = StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) { first }
            provider.awaitCore()
            val storage = InMemoryCoreStorage()

            assertFailsWith<IllegalStateException> { provider.resetAccount(storage) }
            assertEquals(1, first.retireCalls)
            assertEquals(1, first.closeCalls)

            first.closeFailure = null
            provider.resetAccount(storage)

            assertEquals(1, first.retireCalls, "retirement already succeeded; a close-only retry must not repeat it")
            assertEquals(2, first.closeCalls)
            assertTrue(storage.cleared)
        }

    /**
     * The other half of the close fence: a failed `retireLocalState()` must
     * leave the native handle open for retry too, and — since it runs first —
     * must never let [FakeCore.close] run at all while it keeps failing.
     */
    @Test
    fun aFailedRetirementIsOwnedUntilRetryAndDoesNotPermitDeletionOrReopening() =
        runTest {
            var builds = 0
            val first = FakeCore(retireLocalStateFailure = IllegalStateException("retirement refused"))
            val settings = InMemoryTelegramSettings()
            settings.write(1234, "hash")
            val provider =
                StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) {
                    builds++
                    first
                }
            provider.awaitCore()
            val storage = InMemoryCoreStorage()
            assertFailsWith<IllegalStateException> { provider.resetAccount(storage) }
            assertFalse(storage.cleared)
            assertNull(provider.core.value)
            assertFailsWith<IllegalStateException> { provider.coreOrNull() }
            assertEquals(1, builds)
            assertEquals(2, first.retireCalls)
            assertEquals(0, first.closeCalls, "a failed retirement must not release the native handle")
            first.retireLocalStateFailure = null
            provider.resetAccount(storage)
            assertEquals(3, first.retireCalls)
            assertEquals(1, first.closeCalls)
            assertTrue(storage.cleared)
            assertEquals(1234, settings.read()?.apiId)
        }

    @Test
    fun cancellationDuringClearStillFinishesCleanupBeforeReopening() =
        runTest {
            val core = FakeCore()
            val settings = InMemoryTelegramSettings()
            settings.write(1234, "hash")
            val provider = StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) { core }
            provider.awaitCore()
            val clearing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var cleared = false
            val reset =
                launch {
                    provider.resetAccount(
                        object : CoreStorage {
                            override suspend fun clear() {
                                clearing.complete(Unit)
                                release.await()
                                cleared = true
                            }
                        },
                    )
                }
            clearing.await()
            reset.cancel()
            runCurrent()
            assertFalse(reset.isCompleted)
            release.complete(Unit)
            reset.join()
            assertTrue(reset.isCancelled)
            assertTrue(core.closed)
            assertTrue(cleared)
            assertNull(provider.core.value)
            assertEquals(1234, settings.read()?.apiId)
        }
}
