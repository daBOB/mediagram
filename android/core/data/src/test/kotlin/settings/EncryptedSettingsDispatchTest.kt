package data.settings

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** Counts the hops onto it, then runs each one where [delegate] would. */
private class CountingDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    var hops = 0

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        hops++
        delegate.dispatch(context, block)
    }
}

/**
 * Every encrypted store opens the keystore on first touch, so each read,
 * write and clear must leave the caller's thread for the store's own
 * dispatcher — a ViewModel on main calls these directly. What the keystore
 * answers on the JVM does not matter here, only that each call hopped.
 */
class EncryptedSettingsDispatchTest {
    @Test
    fun everyReadWriteAndClearRunsOnTheStoresDispatcher() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val stores: List<Pair<String, (CoroutineDispatcher) -> List<suspend () -> Unit>>> =
                listOf(
                    "tmdb" to { d ->
                        val s = EncryptedTmdbSettings(context, d)
                        listOf({ s.read() }, { s.write("key") }, { s.clear() })
                    },
                    "telegram" to { d ->
                        val s = EncryptedTelegramSettings(context, d)
                        listOf({ s.read() }, { s.write(1, "hash") }, { s.clear() })
                    },
                    "lan token" to { d ->
                        val s = EncryptedLanCacheTokenSettings(context, d)
                        listOf({ s.read() }, { s.write("token") }, { s.clear() })
                    },
                    "library" to { d ->
                        val s = EncryptedLibrarySettings(context, d)
                        listOf({ s.read() }, { s.write("handle") }, { s.clear() })
                    },
                )
            for ((name, calls) in stores) {
                val dispatcher = CountingDispatcher(StandardTestDispatcher(testScheduler))
                calls(dispatcher).forEachIndexed { i, call ->
                    runCatching { call() }
                    assertEquals(i + 1, dispatcher.hops, "$name call ${i + 1} ran on the caller's thread")
                }
            }
        }
}
