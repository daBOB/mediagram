package ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import setup.SetupUiState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileAppTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: MobileAppFixture
    private lateinit var controller: ActivityController<MobileAppTestActivity>

    @Before fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = MobileAppFixture()
            MobileAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(MobileAppTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    private fun submit(
        label: String,
        value: String,
    ) {
        compose.onNodeWithText(label).performTextInput(value)
        compose.onNodeWithText("Continue").performClick()
    }

    private fun signIn() {
        compose.onNodeWithText("api_id").performTextInput("1234")
        submit("api_hash", "0123456789abcdef0123456789abcdef")
        submit("Phone number", "+49123456789")
        submit("Login code", "12345")
        if (fixture.passwordRequired) submit("Two-factor password", "only-for-this-request")
        compose.onNodeWithText("Which library should this device read?").assertIsDisplayed()
        compose.onNodeWithText("Family films").assertIsDisplayed()
    }

    private fun ready() {
        signIn()
        compose.onNodeWithText("Family films").performClick()
        compose.onNodeWithText("mediagram").assertDoesNotExist()
        assertEquals(listOf("films"), fixture.installs)
        assertEquals(null, runBlocking { fixture.library.read() })
        compose.runOnUiThread { fixture.installReady.complete(Unit) }
        compose.onNodeWithText("mediagram").assertIsDisplayed()
        compose.onNodeWithText("Which library should this device read?").assertDoesNotExist()
        assertEquals("films", runBlocking { fixture.library.read() })
        assertEquals(SetupUiState.Ready, fixture.setup.state.value)
    }

    @Test fun actualLoginAndLibraryCallbacksEnterTheReadyCatalogOnlyAfterInstallation() {
        ready()
        assertEquals(listOf("+49123456789"), fixture.core.requestedPhones)
        assertEquals(listOf("attempt-1" to "12345"), fixture.signInCalls)
        assertEquals(1234, runBlocking { fixture.telegram.read()?.apiId })
    }

    @Test fun twoFactorCompletionAlsoHandsOffToLibrarySelection() {
        fixture.passwordRequired = true
        ready()
        assertEquals(listOf("only-for-this-request"), fixture.passwordChecks)
    }

    @Test fun losingAuthorizationInTheBackgroundReturnsToUsableSignIn() {
        ready()
        compose.runOnUiThread { controller.pause().stop() }
        val reads = fixture.core.authorizedReads
        fixture.core.authorized = false
        compose.runOnUiThread { controller.restart().start().resume() }
        compose.onNodeWithText("Phone number").assertIsDisplayed()
        compose.onNodeWithText("mediagram").assertDoesNotExist()
        assertEquals(reads + 1, fixture.core.authorizedReads, "entry must not replay the previous login's completion")
        submit("Phone number", "+49987654321")
        compose.onNodeWithText("Login code").assertIsDisplayed()
        assertEquals(listOf("+49123456789", "+49987654321"), fixture.core.requestedPhones)
        submit("Login code", "67890")
        compose.onNodeWithText("mediagram").assertIsDisplayed()
        assertEquals(listOf("attempt-1" to "12345", "attempt-2" to "67890"), fixture.signInCalls)
    }

    @Test fun signingOutInSettingsReturnsToUsableSignInWithoutReplacingTheCore() {
        ready()
        // Settings is its own icon in the compact header's row now, not a
        // menu item — the trimmed ⋮ only still holds the Android-only three.
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        // Compact width opens on the index; Telegram is the row that carries Sign out.
        compose.onNodeWithText("Telegram").performClick()
        // The page scrolls; Sign out sits below the panels above it on a short window.
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Phone number").assertIsDisplayed()
        compose.onNodeWithText("mediagram").assertDoesNotExist()
        assertTrue(fixture.core.signedOut)
        assertEquals(1, fixture.core.signOutCalls)
        assertTrue(fixture.storage.cleared)
        assertEquals(null, runBlocking { fixture.library.read() })
        submit("Phone number", "+49987654321")
        submit("Login code", "67890")
        compose.onNodeWithText("Which library should this device read?").assertIsDisplayed()
        assertEquals(listOf("attempt-1" to "12345", "attempt-2" to "67890"), fixture.signInCalls)
    }
}
