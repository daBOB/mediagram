package ui.tv.catalog

import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Robolectric can check of [TvFetchResultDialog]: the phone's title
 * and the report it was handed, or nothing at all while there is none. OK
 * taking the remote, and Back dismissing it, are the real window manager's
 * and are checked on the emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvFetchResultDialogStateTest : TvScreenStateTest() {
    @Test
    fun aReportIsShownUnderThePhonesTitle() {
        show { TvFetchResultDialog(message = "12 posters fetched.", onDismiss = {}) }

        compose.onNodeWithText("Update library").assertExists()
        compose.onNodeWithText("12 posters fetched.").assertExists()
        compose.onNodeWithText("OK").assertExists()
    }

    @Test
    fun nothingIsShownWhileThereIsNoReport() {
        show { TvFetchResultDialog(message = null, onDismiss = {}) }

        compose.onNodeWithText("Update library").assertDoesNotExist()
    }
}
