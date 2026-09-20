package login

import catalog.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
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
        val vm = LoginViewModel(FakeCore(signInOutcome = AuthOutcome.PASSWORD_NEEDED))
        vm.submitPhone("+49...")
        vm.submitCode("12345")
        assertEquals(LoginUiState.NeedsPassword, vm.state.value)
    }

    @Test
    fun anAlreadyAuthorizedCoreSkipsStraightToAuthorized() = runTest {
        val vm = LoginViewModel(FakeCore(authorized = true))
        assertEquals(LoginUiState.Authorized, vm.state.value)
    }

    @Test
    fun aFailedCodeRequestSurfacesAsFailed() = runTest {
        val vm = LoginViewModel(FakeCore(requestCodeFails = true))
        vm.submitPhone("+49...")
        assertTrue(vm.state.value is LoginUiState.Failed)
        // Nothing is in flight to retry, so the phone number is genuinely
        // the step to ask for again here.
        assertEquals(LoginStep.PHONE, (vm.state.value as LoginUiState.Failed).step)
    }

    @Test
    fun aRejectedCodeIsRetypedWithoutAskingTelegramForAnotherOne() = runTest {
        val core = FakeCore(signInFailures = 1)
        val vm = LoginViewModel(core)
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
        val vm = LoginViewModel(core)
        vm.submitPhone("+49...")
        vm.submitCode("12345")

        vm.submitPassword("wrong")
        assertEquals(LoginStep.PASSWORD, assertIs<LoginUiState.Failed>(vm.state.value).step)

        vm.submitPassword("right")
        assertEquals(LoginUiState.Authorized, vm.state.value)
        assertEquals(1, core.requestCodeCalls, "a retyped password must not cost a second code request")
    }
}
