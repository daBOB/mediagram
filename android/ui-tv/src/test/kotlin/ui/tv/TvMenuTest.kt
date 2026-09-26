package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.films
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/**
 * The menu walked with a remote over the real [TvLibrary]: the masthead's
 * Menu opens the phone's five items as a page, each screen it opens takes
 * the remote on arrival, and Back walks out one step at a time — the
 * screen, then the page with the remote on the item that opened it, then
 * the masthead's Menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvMenuTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun theMenuIsThePhonesFiveItemsInItsOrderAndBackReturnsToTheMastheadsMenu() {
        openMenu()

        compose.onNodeWithText("System").assertIsFocused()
        val order = listOf("System", "Settings", "Update library", "TMDB key…", "Start over")
        val tops = order.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the rows stand in the phone's order")
        compose.onNodeWithText("Artwork and descriptions need a TMDB key").assertExists()

        back()
        compose.onNodeWithText("Menu").assertIsFocused()
    }

    @Test
    fun systemTakesTheRemoteAndBackLandsOnSystemThenTheMasthead() {
        openMenu()
        press(compose.onNodeWithText("System"))

        compose.onNode(hasText("Catalogue")).assertIsFocused()
        compose.onNode(hasText("This app")).assertExists()

        back()
        compose.onNodeWithText("System").assertIsFocused()
        back()
        compose.onNodeWithText("Menu").assertIsFocused()
    }

    @Test
    fun settingsLandsOnChangeLibraryAndBackLandsOnSettings() {
        openMenu()
        press(compose.onNodeWithText("Settings"))

        compose.onNodeWithText("Change library").assertIsFocused()
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Sign out").assertExists()

        back()
        compose.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun aSettingsPanelIsLeftForSettingsBeforeTheMenu() {
        openMenu()
        press(compose.onNodeWithText("Settings"))
        press(compose.onNodeWithText("Application id and hash…"))
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()

        back()
        compose.onNodeWithText("Application id and hash…").assertIsFocused()
        back()
        compose.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun signOutAsksFirstInThePhonesWords() {
        openMenu()
        press(compose.onNodeWithText("Settings"))
        press(compose.onNodeWithText("Sign out"))

        compose.onNodeWithText("Sign out?").assertExists()
        compose.onNodeWithText("The api_id, api_hash and TMDB key stay", substring = true).assertExists()
    }

    @Test
    fun theKeyScreenPutsTheRemoteInItsFieldAndBackLandsOnTheKeyItem() {
        openMenu()
        press(compose.onNodeWithText("TMDB key…"))

        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()
        compose.onNodeWithText("No key is stored.", substring = true).assertExists()

        back()
        compose.onNodeWithText("TMDB key…").assertIsFocused()
    }

    @Test
    fun startOverAsksFirstInThePhonesWords() {
        openMenu()
        press(compose.onNodeWithText("Start over"))

        compose.onNodeWithText("Start over?").assertExists()
        compose.onNodeWithText("All of it has to be entered again.", substring = true).assertExists()
    }

    @Test
    fun updateLibraryGoesToTheShelves() {
        openMenu()
        press(compose.onNodeWithText("Update library"))

        compose.onNodeWithText("Menu").assertExists()
        compose.onNodeWithText("Update library").assertDoesNotExist()
    }

    @Test
    fun settingsOffersEveryCacheVolumeAndChoosingOneReachesTheModel() {
        openMenu()
        press(compose.onNodeWithText("Settings"))
        compose.onNodeWithText("Where").assertExists()
        compose.onNodeWithText("●  Internal storage", substring = true).assertExists()
        press(compose.onNodeWithText("○  USB drive", substring = true))
        verify { fixture.cacheBudget.chooseVolume("6BBF-D2D8") }
    }

    @Test
    fun settingsShowsTheHomeCacheServerAndAnAcceptedAddressReturnsToItsRow() {
        every { fixture.lanCache.setManualAddress("192.168.0.9:7788") } returns true
        openMenu()
        press(compose.onNodeWithText("Settings"))
        compose.onNodeWithText("Not found").assertExists()
        compose.onNodeWithText("Use the home cache server — on").assertExists()
        press(compose.onNodeWithText("Server address — found on the network"))
        compose.onNodeWithText("Home cache server address").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("192.168.0.9:7788")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        verify { fixture.lanCache.setManualAddress("192.168.0.9:7788") }
        compose.onNodeWithText("Server address — found on the network").assertIsFocused()
    }

    @Test
    fun aRefusedTokenKeepsItsQuestionOpen() {
        every { fixture.lanCache.saveToken("short") } returns false
        openMenu()
        press(compose.onNodeWithText("Settings"))
        press(compose.onNodeWithText("Pairing token — none"))
        compose.onNode(hasSetTextAction()).performTextInput("short")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("Pair with the home cache server").assertExists()
        fixture.lanCacheState.value = fixture.lanCacheState.value?.copy(tokenError = "Too short to be a pairing token.")
        compose.waitForIdle()
        compose.onNodeWithText("Too short to be a pairing token.").assertExists()
    }

    @Test
    fun reopeningAQuestionClearsTheLastRefusal() {
        openMenu()
        press(compose.onNodeWithText("Settings"))
        press(compose.onNodeWithText("Server address — found on the network"))
        verify { fixture.lanCache.clearErrors() }
    }

    @Test
    fun theSwitchRowTurnsTheServerOff() {
        openMenu()
        press(compose.onNodeWithText("Settings"))
        press(compose.onNodeWithText("Use the home cache server — on"))
        verify { fixture.lanCache.setEnabled(false) }
    }

    private fun openMenu() {
        press(compose.onNodeWithText("Menu"))
    }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
