package login

import catalog.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import uniffi.mediagram_core.AuthOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }
}
