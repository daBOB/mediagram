package data

import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
import settings.TelegramCredentials
import uniffi.mediagram_core.AccountSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val OLD_HASH = "0123456789abcdef0123456789abcdef"
private const val NEW_HASH = "fedcba9876543210fedcba9876543210"

class CoreProviderReplaceTest {
    /** Everything a build did, in order, so the close-before-build rule can be read off it. */
    private val log = mutableListOf<String>()

    private fun provider(
        settings: InMemoryTelegramSettings,
        answers: (TelegramCredentials) -> Boolean,
    ) = StoredCoreProvider(settings) { credentials ->
        log += "build ${credentials.apiId}"
        val core =
            FakeCore(
                account =
                    if (answers(credentials)) {
                        Result.success(AccountSummary("A Viewer", null))
                    } else {
                        Result.failure(IllegalStateException("API_ID_INVALID"))
                    },
            )
        object : CoreClient by core {
            override fun close() {
                log += "close ${credentials.apiId}"
                core.close()
            }
        }
    }

    /** One data directory, one auth key: the old core is closed before the new one exists. */
    @Test
    fun theOldCoreIsClosedBeforeTheNewOneIsBuiltAndTheNewIdentityKept() =
        runTest {
            val settings = InMemoryTelegramSettings().apply { write(1, OLD_HASH) }
            val provider = provider(settings) { true }
            provider.awaitCore()

            provider.replace(2, NEW_HASH)

            assertEquals(listOf("build 1", "close 1", "build 2"), log)
            assertEquals(TelegramCredentials(2, NEW_HASH), settings.read())
            assertSame(provider.coreOrNull(), provider.core.value)
        }

    /**
     * A mistyped hash builds fine and fails at Telegram. It must not be kept,
     * nor leave the app without a core: it is closed, and the identity that
     * worked comes back on the next ask.
     */
    @Test
    fun anIdentityTelegramRefusesIsClosedAndThePreviousOneComesBack() =
        runTest {
            val settings = InMemoryTelegramSettings().apply { write(1, OLD_HASH) }
            val provider = provider(settings) { it.apiId == 1 }
            provider.awaitCore()

            assertFailsWith<IllegalStateException> { provider.replace(2, NEW_HASH) }

            assertEquals(TelegramCredentials(1, OLD_HASH), settings.read())
            assertTrue(log.containsAll(listOf("build 2", "close 2")))
            provider.awaitCore()
            assertEquals("build 1", log.last(), "the previous identity is rebuilt on the next ask")
        }
}
