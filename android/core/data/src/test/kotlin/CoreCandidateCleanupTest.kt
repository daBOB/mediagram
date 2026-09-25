package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTelegramSettings
import settings.TelegramCredentials
import settings.TelegramSettings
import uniffi.mediagram_core.AccountSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val CANDIDATE_HASH = "0123456789abcdef0123456789abcdef"

class CoreCandidateCleanupTest {
    @Test
    fun supplyingClosesAnUnpersistedCandidateAndAllowsRetry() = persistenceFailure(replacing = false, closeFails = false)

    @Test
    fun replacingClosesAnUnpersistedCandidateAndAllowsRetry() = persistenceFailure(replacing = true, closeFails = false)

    @Test
    fun supplyPreservesPersistenceFailureWhenClosingAlsoFails() = persistenceFailure(replacing = false, closeFails = true)

    @Test
    fun replacePreservesPersistenceFailureWhenClosingAlsoFails() = persistenceFailure(replacing = true, closeFails = true)

    private fun persistenceFailure(
        replacing: Boolean,
        closeFails: Boolean,
    ) = runTest {
        val stored = InMemoryTelegramSettings()
        if (replacing) stored.write(1, CANDIDATE_HASH)
        val refusal = SecurityException("credential storage unavailable")
        var writeFailure: Throwable? = refusal
        val settings =
            object : TelegramSettings by stored {
                override suspend fun write(
                    apiId: Int,
                    apiHash: String,
                ) {
                    writeFailure?.let { throw it }
                    stored.write(apiId, apiHash)
                }
            }
        val closeFailure = IllegalArgumentException("candidate close failed").takeIf { closeFails }
        val old = ClosingCandidate()
        val rejected = ClosingCandidate(closeFailure)
        val retried = ClosingCandidate()
        val candidates = ArrayDeque(listOf(rejected, retried))
        val provider =
            StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) {
                if (it.apiId == 1) old else candidates.removeFirst()
            }
        if (replacing) provider.awaitCore()

        suspend fun install() {
            if (replacing) provider.replace(2, CANDIDATE_HASH) else provider.supply(2, CANDIDATE_HASH)
        }

        val failure = assertFailsWith<SecurityException> { install() }

        assertTrue(failureChain(failure).any { it === refusal }, "original persistence exception was lost: $failure")
        assertEquals(1, rejected.closes)
        assertEquals(if (replacing) 1 else 0, old.closes)
        assertNull(provider.core.value)
        assertEquals(if (replacing) TelegramCredentials(1, CANDIDATE_HASH) else null, stored.read())
        if (closeFailure != null) {
            assertTrue(
                failureChain(failure).any { closeFailure in it.suppressed },
                failureChain(failure).joinToString { "${it.javaClass.name}: ${it.suppressed.toList()}" },
            )
        }
        writeFailure = null
        install()
        assertSame(retried, provider.awaitCore())
        assertEquals(0, retried.closes)
        assertEquals(TelegramCredentials(2, CANDIDATE_HASH), stored.read())
    }

    @Test
    fun accountFailureRemainsPrimaryWhenCandidateCloseFails() =
        runTest {
            val refusal = IllegalStateException("account refused")
            val closeFailure = IllegalArgumentException("candidate close failed")
            val candidate = ClosingCandidate(closeFailure, Result.failure(refusal))
            val provider = StoredCoreProvider(InMemoryTelegramSettings(), StandardTestDispatcher(testScheduler)) { candidate }

            val failure = assertFailsWith<IllegalStateException> { provider.replace(2, CANDIDATE_HASH) }

            assertTrue(failureChain(failure).any { it === refusal })
            assertTrue(failureChain(failure).any { closeFailure in it.suppressed })
            assertEquals(1, candidate.closes)
            assertNull(provider.core.value)
        }

    @Test
    fun cancelledAccountValidationStillClosesItsUnpublishedCandidate() =
        runTest {
            val gate = CompletableDeferred<AccountSummary>()
            val closed = ClosingCandidate()
            val candidate =
                object : CoreClient by closed {
                    override suspend fun account(): AccountSummary = gate.await()
                }
            val provider = StoredCoreProvider(InMemoryTelegramSettings(), StandardTestDispatcher(testScheduler)) { candidate }
            val replacing = launch { provider.replace(2, CANDIDATE_HASH) }
            runCurrent()

            replacing.cancelAndJoin()

            assertEquals(1, closed.closes)
            assertNull(provider.core.value)
        }

    @Test
    fun cancellationAtSupplyConstructionHandoffStillClosesTheCandidate() = cancelledConstruction(replacing = false)

    @Test
    fun cancellationAtReplacementConstructionHandoffStillClosesTheCandidate() = cancelledConstruction(replacing = true)

    @Test
    fun cancellationAtStoredIdentityConstructionHandoffStillClosesTheCandidate() =
        cancelledConstruction(replacing = false, restoring = true)

    private fun cancelledConstruction(
        replacing: Boolean,
        restoring: Boolean = false,
    ) = runTest {
        val candidate = ClosingCandidate()
        lateinit var installing: Job
        val settings = InMemoryTelegramSettings()
        if (restoring) settings.write(2, CANDIDATE_HASH)
        val provider =
            StoredCoreProvider(settings, StandardTestDispatcher(testScheduler)) {
                installing.cancel()
                candidate
            }
        installing =
            launch {
                when {
                    restoring -> provider.coreOrNull()
                    replacing -> provider.replace(2, CANDIDATE_HASH)
                    else -> provider.supply(2, CANDIDATE_HASH)
                }
            }
        installing.join()

        assertTrue(installing.isCancelled)
        assertEquals(1, candidate.closes)
        assertNull(provider.core.value)
    }
}

private fun failureChain(failure: Throwable) = generateSequence(failure) { it.cause }

private class ClosingCandidate(
    private val closeFailure: Throwable? = null,
    account: Result<AccountSummary> = Result.success(AccountSummary("A Viewer", null)),
) : CoreClient by FakeCore(account = account) {
    var closes = 0

    override fun close() {
        closes++
        closeFailure?.let { throw it }
    }
}
