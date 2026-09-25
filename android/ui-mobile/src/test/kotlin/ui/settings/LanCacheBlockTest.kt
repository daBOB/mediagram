package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import system.LanCacheConnection
import system.LanCacheUiState
import kotlin.test.assertEquals

private fun state(
    connection: LanCacheConnection = LanCacheConnection.NOT_FOUND,
    enabled: Boolean = true,
    hasToken: Boolean = false,
    manualAddress: String = "",
    connectedHost: String? = null,
    heldBytes: Long? = null,
    tokenRejected: Boolean = false,
    addressError: String? = null,
    tokenError: String? = null,
) = LanCacheUiState(
    enabled,
    hasToken,
    manualAddress,
    connection,
    connectedHost,
    heldBytes,
    tokenRejected,
    addressError,
    tokenError,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanCacheBlockTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(
        state: LanCacheUiState?,
        onSetEnabled: (Boolean) -> Unit = {},
        onSaveManualAddress: (String) -> Unit = {},
        onSaveToken: (String) -> Unit = {},
        onGrantPermission: () -> Unit = {},
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    LanCacheBlockContent(state, onSetEnabled, onSaveManualAddress, onSaveToken, onGrantPermission)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theFourStatusWordsMatchTheirConnectionState() {
        assertEquals("Searching", lanCacheStatusLine(state(LanCacheConnection.SEARCHING)))
        assertEquals("Not found", lanCacheStatusLine(state(LanCacheConnection.NOT_FOUND)))
        assertEquals("Needs local network permission", lanCacheStatusLine(state(LanCacheConnection.NEEDS_PERMISSION)))
        assertEquals(
            "Connected to 10.0.0.5:7788, holding 1.0 MB",
            lanCacheStatusLine(
                state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576),
            ),
        )
    }

    @Test
    fun aConnectedServerWithNoHeldFigureOmitsTheComma() {
        assertEquals(
            "Connected to 10.0.0.5:7788",
            lanCacheStatusLine(state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = null)),
        )
    }

    @Test
    fun aNullStateRendersNothing() {
        show(null)
        compose.onNodeWithText("Home cache server").assertDoesNotExist()
    }

    @Test
    fun theStatusRowAndTheTokenStoredNoticeAreShown() {
        show(state(hasToken = true))
        compose.onNodeWithText("Home cache server").assertIsDisplayed()
        compose.onNodeWithText("Not found").assertIsDisplayed()
        compose.onNodeWithText("A pairing token is stored.").assertIsDisplayed()
    }

    @Test
    fun aRejectedTokenShowsTheNotice() {
        show(state(tokenRejected = true))
        compose.onNodeWithText("Pairing token rejected.").assertIsDisplayed()
    }

    @Test
    fun togglingTheSwitchCallsBack() {
        var calledWith: Boolean? = null
        show(state(enabled = false), onSetEnabled = { calledWith = it })
        compose.onNodeWithText("Use the home cache server").performClick()
        assertEquals(true, calledWith)
    }

    @Test
    fun savingTheAddressCallsBackWithWhatWasTyped() {
        var saved: String? = null
        show(state(), onSaveManualAddress = { saved = it })
        compose.onNodeWithText("Server address (optional — leave blank to rely on discovery)").performTextInput("192.168.1.9:7788")
        compose.onNodeWithText("Save address").performClick()
        assertEquals("192.168.1.9:7788", saved)
    }

    @Test
    fun savingTheTokenCallsBackAndClearsTheField() {
        var saved: String? = null
        show(state(), onSaveToken = { saved = it })
        compose.onNodeWithText("Pairing token").performTextInput("a".repeat(64))
        compose.onNodeWithText("Save token").performClick()
        assertEquals("a".repeat(64), saved)
    }

    @Test
    fun theGrantActionShowsOnlyWhenPermissionIsNeeded() {
        show(state(LanCacheConnection.NOT_FOUND))
        compose.onNodeWithText("Grant local network access").assertDoesNotExist()
    }

    @Test
    fun theGrantActionCallsBackWhenShown() {
        var granted = false
        show(state(LanCacheConnection.NEEDS_PERMISSION), onGrantPermission = { granted = true })
        compose.onNodeWithText("Grant local network access").performClick()
        assertEquals(true, granted)
    }

    @Test
    fun anAddressErrorIsShownUnderTheField() {
        show(state(addressError = "Could not understand that address."))
        compose.onNodeWithText("Could not understand that address.").assertIsDisplayed()
    }

    @Test
    fun aTokenErrorIsShownUnderTheField() {
        show(state(tokenError = "That does not look like a pairing token."))
        compose.onNodeWithText("That does not look like a pairing token.").assertIsDisplayed()
    }
}
