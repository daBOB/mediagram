package ui

import androidx.lifecycle.viewModelScope
import catalog.CatalogUiState
import catalog.CatalogViewModel
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreProvider
import data.DefaultCatalogRepository
import data.LibraryUpdate
import data.RefreshLog
import data.RefreshOutcome
import data.settings.InMemoryLibrarySettings
import data.settings.InMemoryTmdbSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import setup.login.LoginStep
import setup.login.LoginUiState
import setup.login.LoginViewModel
import testing.FakeCore
import testing.FakeCoreProvider
import testing.FakeTogetherLive
import testing.WatchStateFixture
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.FetchReport
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FailureDiagnosticsTest {
    @Before fun prepare() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ShadowLog.clear()
    }

    @After fun restore() = Dispatchers.resetMain()

    @Test fun loginKeepsOriginalFailuresInDiagnosticsWithoutPuttingArgumentsInLogMessages() =
        runTest {
            for (step in LoginStep.entries) {
                ShadowLog.clear()
                val core = loginCore()
                val model = LoginViewModel(provider(core), UnconfinedTestDispatcher())
                try {
                    if (step != LoginStep.PHONE) model.submitPhone("private-phone")
                    if (step == LoginStep.PASSWORD) model.submitCode("private-code")
                    val failure = IOException("private-storage-path")
                    failLogin(core, model, step, failure)
                    val shown = model.state.value as LoginUiState.Failed
                    assertEquals(step, shown.step)
                    assertFalse(shown.message.contains("private"))
                    val diagnostic = ShadowLog.getLogsForTag("Login").single()
                    assertSame(failure, diagnostic.throwable)
                    assertFalse(diagnostic.msg.contains("private"))
                } finally {
                    model.viewModelScope.cancel()
                }
            }
        }

    @Test fun cancelledLoginStepsDoNotBecomeDiagnosticsOrRefusals() =
        runTest {
            for (step in LoginStep.entries) {
                val core = loginCore()
                val model = LoginViewModel(provider(core), UnconfinedTestDispatcher())
                try {
                    if (step != LoginStep.PHONE) model.submitPhone("private-phone")
                    if (step == LoginStep.PASSWORD) model.submitCode("private-code")
                    val before = model.state.value
                    failLogin(core, model, step, CancellationException("leaving"))
                    assertEquals(before, model.state.value)
                    assertTrue(ShadowLog.getLogsForTag("Login").isEmpty())
                } finally {
                    model.viewModelScope.cancel()
                }
            }
        }

    @Test fun aRefusedRefreshKeepsItsOriginalCauseInDiagnostics() =
        runTest {
            val failure = IOException("private-catalog-path")
            val core =
                object : CoreInterface by FakeCore() {
                    override suspend fun refreshLibrary(handle: String): ULong = throw failure
                }
            val library = InMemoryLibrarySettings().apply { write("a1b2c3") }
            val model = catalogModel(DefaultCatalogRepository(FakeCoreProvider(core), library, RefreshLog(), Dispatchers.Unconfined))
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                runCurrent()
                assertEquals(CatalogUiState.Failed("Could not refresh the library"), model.state.value)
                val diagnostic = ShadowLog.getLogsForTag("catalog").single()
                // Under test the coroutine debug mode rethrows a copy across withContext, caused by the original.
                assertTrue(generateSequence(diagnostic.throwable) { it.cause }.any { it === failure })
                assertFalse(diagnostic.msg.contains("private"))
            } finally {
                model.viewModelScope.cancel()
            }
        }

    @Test fun catalogReadsAndArtworkRereadsKeepOriginalFailuresWithControlledCopy() =
        runTest {
            for (regroup in listOf(false, true)) {
                ShadowLog.clear()
                val repository = catalogRepository()
                val model = catalogModel(repository, if (regroup) fetchingArtwork() else idleEnrichment())
                try {
                    val failure = IOException("private-catalog-path")
                    if (!regroup) coEvery { repository.sets() } throws failure
                    val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                    runCurrent()
                    if (regroup) {
                        failSecondRead(repository, failure)
                        model.update()
                        runCurrent()
                    }
                    assertEquals(CatalogUiState.Failed("Could not read the library. Try again."), model.state.value)
                    assertSame(failure, ShadowLog.getLogsForTag("Catalog").single().throwable)
                    subscription.cancel()
                } finally {
                    model.viewModelScope.cancel()
                }
            }
        }

    @Test fun cancelledCatalogWorkNeverProducesFailureDiagnostics() =
        runTest {
            for (operation in listOf("refresh", "read", "regroup")) {
                val repository = catalogRepository()
                val model = catalogModel(repository, if (operation == "regroup") fetchingArtwork() else idleEnrichment())
                try {
                    val cancelled = CancellationException("leaving")
                    // CatalogRepository.refresh throws cancellation rather than returning it as a failure.
                    if (operation == "refresh") coEvery { repository.refresh() } throws cancelled
                    if (operation == "read") coEvery { repository.sets() } throws cancelled
                    val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                    runCurrent()
                    if (operation == "regroup") {
                        failSecondRead(repository, cancelled)
                        model.update()
                        runCurrent()
                    }
                    assertFalse(model.state.value is CatalogUiState.Failed)
                    assertTrue(ShadowLog.getLogsForTag("Catalog").isEmpty())
                    subscription.cancel()
                } finally {
                    model.viewModelScope.cancel()
                }
            }
        }

    private fun loginCore(): FakeCore = FakeCore(authorized = false, signInOutcome = AuthOutcome.PASSWORD_NEEDED)

    private fun provider(client: FakeCore): CoreProvider = FakeCoreProvider(client)

    private fun failLogin(
        core: FakeCore,
        model: LoginViewModel,
        step: LoginStep,
        failure: Exception,
    ) {
        when (step) {
            LoginStep.PHONE -> {
                core.requestFailure = failure
                model.submitPhone("private-phone")
            }

            LoginStep.CODE -> {
                core.signInFailure = failure
                model.submitCode("private-code")
            }

            LoginStep.PASSWORD -> {
                core.passwordFailure = failure
                model.submitPassword("private-password")
            }
        }
    }

    private fun catalogRepository(): CatalogRepository =
        mockk<CatalogRepository>().also {
            coEvery { it.publishedAt() } returns null
            coEvery { it.refresh() } returns RefreshOutcome.AlreadyCurrent
            coEvery { it.sets() } returns emptyList()
        }

    /** The update's own read after its refresh succeeds; the one after the artwork lands fails with [failure]. */
    private fun failSecondRead(
        repository: CatalogRepository,
        failure: Exception,
    ) {
        var reads = 0
        coEvery { repository.sets() } coAnswers { if (++reads == 2) throw failure else emptyList() }
    }

    private fun idleEnrichment() = CatalogEnrichmentFetcher(mockk(), InMemoryTmdbSettings())

    /** A manual update that lays a poster down, so the update reads the library again. */
    private suspend fun fetchingArtwork() =
        CatalogEnrichmentFetcher(
            FakeCoreProvider(FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u))),
            InMemoryTmdbSettings().apply { write("key") },
            dispatcher = Dispatchers.Unconfined,
        )

    private fun catalogModel(
        repository: CatalogRepository,
        enrichment: CatalogEnrichmentFetcher = idleEnrichment(),
    ): CatalogViewModel =
        CatalogViewModel(repository, WatchStateFixture().repository, LibraryUpdate(repository, enrichment), togetherLive = FakeTogetherLive(), shelving = Dispatchers.Main.immediate)
}
