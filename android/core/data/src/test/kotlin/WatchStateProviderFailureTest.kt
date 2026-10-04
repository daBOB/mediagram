package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The core's own state writes never throw, so the provider is where a
 * repository write fails or stalls — injected through
 * [FakeCoreProvider.beforeCore], with the real repository and core rules
 * on either side of it.
 */
class WatchStateProviderFailureTest {
    private val core =
        FakeCore().apply {
            profiles = listOf(CoreProfile("p1", "Alice"))
            chosen = "p1"
        }
    private val provider = FakeCoreProvider(core)
    private val repository = DefaultWatchStateRepository(provider, dispatcher = Dispatchers.Unconfined)

    @Test
    fun aWriteTheProviderCannotServeThrowsAndWritesNothing() =
        runTest {
            repository.reload()
            provider.beforeCore = { error("keystore unavailable") }

            val thrown = assertFailsWith<IllegalStateException> { repository.setWatchlisted("s1", true) }

            assertEquals("keystore unavailable", thrown.message)
            assertEquals(emptyList(), core.snapshot("p1").watchlist)
            assertEquals(emptyList(), repository.snapshot.value.watchlist)
        }

    @Test
    fun aWriteHeldByTheProviderLandsOnlyOnceReleased() =
        runTest {
            repository.reload()
            val release = CompletableDeferred<Unit>()
            provider.beforeCore = { release.await() }

            val write = launch { repository.setWatchlisted("s1", true) }
            runCurrent()
            assertEquals(emptyList(), core.snapshot("p1").watchlist)

            release.complete(Unit)
            write.join()
            assertEquals(listOf("s1"), repository.snapshot.value.watchlist)
        }
}
