package ui

import androidx.lifecycle.viewModelScope
import catalog.CatalogUiState
import catalog.CatalogViewModel
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreClient
import data.CoreProvider
import data.LibraryUpdateCoordinator
import data.WatchStateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.WatchSnapshot
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import settings.InMemoryTmdbSettings
import setup.login.LoginStep
import setup.login.LoginUiState
import setup.login.LoginViewModel
import uniffi.mediagram_core.AuthOutcome
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

    @Test fun consumedRefreshResultKeepsItsOriginalCauseInDiagnostics() =
        runTest {
            val failure = IOException("private-catalog-path")
            val repository = catalogRepository()
            coEvery { repository.refresh() } returns Result.failure(failure)
            val model = catalogModel(repository)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                runCurrent()
                assertEquals(CatalogUiState.Failed("Could not refresh the library"), model.state.value)
                assertSame(failure, ShadowLog.getLogsForTag("Catalog").single().throwable)
            } finally {
                model.viewModelScope.cancel()
            }
        }

    @Test fun catalogReadsAndArtworkRereadsKeepOriginalFailuresWithControlledCopy() =
        runTest {
            for (regroup in listOf(false, true)) {
                ShadowLog.clear()
                val repository = catalogRepository()
                val model = catalogModel(repository)
                try {
                    val failure = IOException("private-catalog-path")
                    if (!regroup) coEvery { repository.sets() } throws failure
                    val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                    runCurrent()
                    if (regroup) {
                        coEvery { repository.sets() } throws failure
                        model.showFetched()
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
                val model = catalogModel(repository)
                try {
                    val cancelled = CancellationException("leaving")
                    if (operation == "refresh") coEvery { repository.refresh() } returns Result.failure(cancelled)
                    if (operation == "read") coEvery { repository.sets() } throws cancelled
                    val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
                    runCurrent()
                    if (operation == "regroup") {
                        coEvery { repository.sets() } throws cancelled
                        model.showFetched()
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

    private fun loginCore(): CoreClient =
        mockk<CoreClient>().also {
            every { it.isAuthorized() } returns false
            coEvery { it.requestCode(any()) } returns "token"
            coEvery { it.signIn(any(), any()) } returns AuthOutcome.PASSWORD_NEEDED
        }

    private fun provider(client: CoreClient): CoreProvider =
        mockk<CoreProvider>().also {
            every { it.core } returns MutableStateFlow(client)
            coEvery { it.awaitCore() } returns client
        }

    private fun failLogin(
        core: CoreClient,
        model: LoginViewModel,
        step: LoginStep,
        failure: Exception,
    ) {
        when (step) {
            LoginStep.PHONE -> {
                coEvery { core.requestCode(any()) } throws failure
                model.submitPhone("private-phone")
            }

            LoginStep.CODE -> {
                coEvery { core.signIn(any(), any()) } throws failure
                model.submitCode("private-code")
            }

            LoginStep.PASSWORD -> {
                coEvery { core.checkPassword(any()) } throws failure
                model.submitPassword("private-password")
            }
        }
    }

    private fun catalogRepository(): CatalogRepository =
        mockk<CatalogRepository>().also {
            coEvery { it.refresh() } returns Result.success(0)
            coEvery { it.sets() } returns emptyList()
        }

    private fun catalogModel(repository: CatalogRepository): CatalogViewModel {
        val watch = mockk<WatchStateRepository>()
        every { watch.snapshot } returns MutableStateFlow(WatchSnapshot.Empty)
        every { watch.profiles } returns MutableStateFlow(emptyList())
        every { watch.chosenProfileId } returns MutableStateFlow(null)
        val enrichment = CatalogEnrichmentFetcher(mockk(), InMemoryTmdbSettings())
        return CatalogViewModel(repository, watch, LibraryUpdateCoordinator(repository, enrichment))
    }
}
