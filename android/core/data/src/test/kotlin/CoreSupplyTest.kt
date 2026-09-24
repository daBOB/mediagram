package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
import settings.TelegramSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class CoreSupplyTest {
    @Test
    fun repeatedSupplyCannotOverwriteAnInstalledCoreOrItsCredentials() =
        runTest {
            val settings = InMemoryTelegramSettings()
            val built = mutableListOf<FakeCore>()
            val provider = StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) { FakeCore().also(built::add) }
            provider.supply(1, "first")
            val first = provider.core.value

            val result = runCatching { provider.supply(2, "second") }

            assertIs<IllegalStateException>(result.exceptionOrNull())
            assertSame(first, provider.core.value)
            assertEquals(1, built.size)
            assertFalse(built.single().closed)
            assertEquals(1, settings.read()?.apiId)
        }

    @Test
    fun twoConcurrentSuppliesCannotBothPublish() =
        runTest {
            val stored = InMemoryTelegramSettings()
            val writing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val settings =
                object : TelegramSettings by stored {
                    override suspend fun write(
                        apiId: Int,
                        apiHash: String,
                    ) {
                        if (apiId == 1) {
                            writing.complete(Unit)
                            release.await()
                        }
                        stored.write(apiId, apiHash)
                    }
                }
            var builds = 0
            val provider =
                StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) {
                    builds++
                    FakeCore()
                }
            val first = async { provider.supply(1, "first") }
            writing.await()
            val second = async { runCatching { provider.supply(2, "second") } }
            release.complete(Unit)
            first.await()
            assertIs<IllegalStateException>(second.await().exceptionOrNull())
            assertEquals(1, builds)
            assertEquals(1, stored.read()?.apiId)
        }
}
