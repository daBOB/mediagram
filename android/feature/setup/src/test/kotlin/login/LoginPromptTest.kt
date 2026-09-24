package setup.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The field the screen puts up decides which call the Continue button
 * makes, so a rejection landing on the wrong field silently throws away a
 * login attempt that was still usable.
 */
class LoginPromptTest {
    @Test
    fun aRejectedCodeAsksForTheCodeAgain() {
        assertEquals(
            LoginStep.CODE,
            promptFor(LoginUiState.Failed(LoginStep.CODE, "the code was not accepted")),
        )
    }

    @Test
    fun aRejectedPasswordAsksForThePasswordAgain() {
        assertEquals(
            LoginStep.PASSWORD,
            promptFor(LoginUiState.Failed(LoginStep.PASSWORD, "the password was not accepted")),
        )
    }

    @Test
    fun aFailedCodeRequestFallsBackToThePhoneNumber() {
        assertEquals(
            LoginStep.PHONE,
            promptFor(LoginUiState.Failed(LoginStep.PHONE, "could not request a code")),
        )
    }

    @Test
    fun eachPendingStepAsksForItsOwnCredential() {
        assertEquals(LoginStep.PHONE, promptFor(LoginUiState.NeedsPhone))
        assertEquals(LoginStep.CODE, promptFor(LoginUiState.NeedsCode))
        assertEquals(LoginStep.PASSWORD, promptFor(LoginUiState.NeedsPassword))
        assertNull(promptFor(LoginUiState.Authorized))
    }
}
