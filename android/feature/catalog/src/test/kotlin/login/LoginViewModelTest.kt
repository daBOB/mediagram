package login

import catalog.MainDispatcherRule
import data.StoredCoreProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryTelegramSettings
import uniffi.mediagram_core.AuthOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aCodeThatNeedsTwoFactorAsksForThePassword() = runTest {
        val vm = LoginViewModel(
            ResolvedCoreProvider(FakeCore(signInOutcome = AuthOutcome.PASSWORD_NEEDED)),
            UnconfinedTestDispatcher(),
        )
        vm.submitPhone("+49...")
        vm.submitCode("12345")
        assertEquals(LoginUiState.NeedsPassword, vm.state.value)
    }

    @Test
    fun anAlreadyAuthorizedCoreSkipsStraightToAuthorized() = runTest {
        val vm = LoginViewModel(ResolvedCoreProvider(FakeCore(authorized = true)), UnconfinedTestDispatcher())
        assertEquals(LoginUiState.Authorized, vm.state.value)
    }

    @Test
    fun aFailedCodeRequestSurfacesAsFailed() = runTest {
        val vm = LoginViewModel(ResolvedCoreProvider(FakeCore(requestCodeFails = true)), UnconfinedTestDispatcher())
        vm.submitPhone("+49...")
        assertTrue(vm.state.value is LoginUiState.Failed)
        // Nothing is in flight to retry, so the phone number is genuinely
        // the step to ask for again here.
        assertEquals(LoginStep.PHONE, (vm.state.value as LoginUiState.Failed).step)
    }

    @Test
    fun aRejectedCodeIsRetypedWithoutAskingTelegramForAnotherOne() = runTest {
        val core = FakeCore(signInFailures = 1)
        val vm = LoginViewModel(ResolvedCoreProvider(core), UnconfinedTestDispatcher())
        vm.submitPhone("+49...")

        vm.submitCode("00000")
        assertEquals(LoginStep.CODE, assertIs<LoginUiState.Failed>(vm.state.value).step)

        vm.submitCode("12345")
        assertEquals(LoginUiState.Authorized, vm.state.value)
        assertEquals(1, core.requestCodeCalls, "a retyped code must not cost a second code request")
    }

    @Test
    fun aRejectedPasswordIsRetypedWithoutRestartingTheSignIn() = runTest {
        val core = FakeCore(signInOutcome = AuthOutcome.PASSWORD_NEEDED, passwordFailures = 1)
        val vm = LoginViewModel(ResolvedCoreProvider(core), UnconfinedTestDispatcher())
        vm.submitPhone("+49...")
        vm.submitCode("12345")

        vm.submitPassword("wrong")
        assertEquals(LoginStep.PASSWORD, assertIs<LoginUiState.Failed>(vm.state.value).step)

        vm.submitPassword("right")
        assertEquals(LoginUiState.Authorized, vm.state.value)
        assertEquals(1, core.requestCodeCalls, "a retyped password must not cost a second code request")
    }

    /**
     * This ViewModel belongs to the Activity and outlives the screen that
     * shows it, so after signing this device out it is still the same
     * instance. Reporting the sign-in that was just undone would leave the
     * sign-in step with nothing to ask for, and it renders nothing at all
     * in that state — an empty screen no amount of tapping recovers from.
     */
    @Test
    fun signingThisDeviceOutSendsTheSignInStepBackToThePhoneNumber() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val settings = InMemoryTelegramSettings()
        // The second core reports no session because starting over deleted
        // the auth key file the first one had been reading.
        val cores = ArrayDeque(listOf(FakeCore(authorized = true), FakeCore(authorized = false)))
        val provider = StoredCoreProvider(settings, dispatcher) { cores.removeFirst() }
        provider.supply(1234, "0123456789abcdef0123456789abcdef")
        val vm = LoginViewModel(provider, dispatcher)
        assertEquals(LoginUiState.Authorized, vm.state.value)

        provider.forget()
        provider.supply(5678, "fedcba9876543210fedcba9876543210")

        assertEquals(LoginUiState.NeedsPhone, vm.state.value)
    }
}
