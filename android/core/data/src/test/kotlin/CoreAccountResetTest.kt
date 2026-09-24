package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
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
            var refuse = true
            var closeCalls = 0
            var builds = 0
            val first =
                object : CoreClient by FakeCore() {
                    override fun close() {
                        closeCalls++
                        if (refuse) error("close refused")
                    }
                }
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
            assertEquals(2, closeCalls)
            refuse = false
            provider.resetAccount(storage)
            assertEquals(3, closeCalls)
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
