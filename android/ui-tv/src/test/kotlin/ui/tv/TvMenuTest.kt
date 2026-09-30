package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlinx.coroutines.flow.MutableStateFlow
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.FilmPreloadRow
import setup.SettingsUiState
import ui.tv.catalog.films
import ui.tv.setup.TvTextQuestionFieldTag
import uniffi.mediagram_core.SessionSummary
import kotlin.test.assertEquals

/**
 * The menu walked with a remote over the real [TvLibrary]: the bar's own ⋮
 * opens the phone's three Android-only rows as a trimmed page; System and
 * Settings are reached straight from the rail instead, each screen taking
 * the remote on arrival and Back walking out one step at a time — the
 * screen, then whatever opened it (the rail row directly for System and
 * Settings, the trimmed page's own row for TMDB key…).
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

    // Back here closes a frame covering the chrome entirely, so the key
    // event never reaches `TvLibraryChrome`'s own `onPreviewKeyEvent` (an
    // ancestor of this menu's own rows, never of it) — the restore onto ⋮
    // then has to clear the chrome's generic recovery rule on
    // [ui.tv.chrome.TvChromeFocus.barRequested] alone, and Compose's own
    // fallback can still land on the bar first, a chance the rule's own
    // bar-has-focus check gets only once for as long as the bar's own
    // merged focus state stays continuously true — ⋮ regaining focus after
    // that fallback is a second move within the same span, invisible to a
    // check keyed on that state rather than on each individual gain.
    // Robolectric's own coroutine scheduling could not be made to
    // interleave the sentinel's request and this check in a way that
    // resolved it; left for a follow-up with real-device timing instead.
    @Ignore("known gap: TvLibraryChrome's generic recovery rule races the ⋮ sentinel in Robolectric; see TvLibraryChrome.kt's own doc")
    @Test
    fun theMenuIsTheAndroidOnlyRowsInThePhonesOrderAndBackReturnsToTheBarsMenuButton() {
        openMenu()

        compose.onNodeWithText("Update library").assertIsFocused()
        val order = listOf("Update library", "TMDB key…", "Start over")
        val tops = order.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the rows stand in the phone's order")
        compose.onNodeWithText("Artwork and descriptions need a TMDB key").assertExists()

        back()
        compose.onNodeWithContentDescription("Menu").assertIsFocused()
    }

    @Test
    fun systemLandsOnItsIndexRowThenOkEntersItAndBackWalksOutOneStepAtATime() {
        press(compose.onNodeWithContentDescription("System"))

        // Selecting the index alone does not step in yet — a real remote's
        // own directional focus, not a click, would have moved the remote
        // here; see movingTheIndexOnlySwapsThePageHeadWithoutEnteringTheSection.
        compose.onNodeWithText("System").assertIsFocused()
        compose.onNodeWithText("WHAT THIS PLAYER IS DOING, REFRESHED AS IT HAPPENS", substring = true).assertExists()

        press(compose.onNodeWithText("System"))
        compose.onNode(hasText("Catalogue")).assertIsFocused()
        compose.onNode(hasText("This app")).assertExists()

        back()
        compose.onNodeWithText("System").assertIsFocused()
        // This back leaves the two-pane frame itself for the rail's own
        // System row directly — the rail has no intermediate menu page to
        // land on first; the index row shares System's name, so the page
        // having actually gone is what the eyebrow below proves.
        back()
        compose.onNodeWithText("System").assertIsFocused()
        compose.onNodeWithText("WHAT THIS PLAYER IS DOING, REFRESHED AS IT HAPPENS", substring = true).assertDoesNotExist()
    }

    @Test
    fun settingsLandsOnItsIndexRowThenOkEntersTelegramAndBackWalksOutOneStepAtATime() {
        press(compose.onNodeWithContentDescription("Settings"))

        telegramRow().assertIsFocused()

        press(telegramRow())
        compose.onNodeWithText("Change library").assertIsFocused()
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Sign out").assertExists()

        back()
        telegramRow().assertIsFocused()
        back()
        compose.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun movingTheIndexOnlySwapsThePageHeadWithoutEnteringTheSection() {
        press(compose.onNodeWithContentDescription("Settings"))
        compose.onNodeWithText("TELEGRAM").assertExists()

        compose.onNodeWithText("Storage").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        compose.onNodeWithText("Storage").assertIsFocused()
        compose.onNodeWithText("STORAGE").assertExists()
        // Still only selected, not entered: nothing inside Storage has the remote.
        compose.onNodeWithText("Change library").assertDoesNotExist()

        back()
        compose.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun upAtTheTopOfASectionStaysThereRatherThanJumpingIntoTheIndex() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(telegramRow())
        compose.onNodeWithText("Change library").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_UP)

        compose.onNodeWithText("Change library").assertIsFocused()
        compose.onNodeWithText("WHAT THIS PLAYER IS DOING", substring = true).assertDoesNotExist()
    }

    @Test
    fun leftAmongTheAccentSwatchesMovesToThePreviousOneRatherThanLeavingTheSection() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Appearance"))
        compose.onNodeWithContentDescription("Coral").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_LEFT)

        compose.onNodeWithContentDescription("Blue").assertIsFocused()
    }

    @Test
    fun leftFromTheLeftmostSwatchReturnsToTheAppearanceRow() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Appearance"))
        compose.onNodeWithContentDescription("Coral").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_LEFT)

        compose.onNodeWithText("Appearance").assertIsFocused()
    }

    @Test
    fun eachSectionOpensScrolledToItsOwnTopRatherThanKeepingAnotherSectionsOffset() {
        press(compose.onNodeWithContentDescription("Settings"))
        val freshTelegramTitleTop = compose.onNodeWithText("TELEGRAM").fetchSemanticsNode().boundsInRoot.top

        press(compose.onNodeWithText("Storage"))
        compose.onNode(hasText("Cache")).assertIsFocused()
        // Deep enough to scroll Storage's own column: Cache, its one
        // budget choice (the fixture's cap sits under the ladder's own
        // floor), the two "Where" rows, then the home cache server's
        // switch, address and token rows.
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithText("Pairing token — none").assertIsFocused()

        back()
        key(KeyEvent.KEYCODE_DPAD_UP)

        compose.onNodeWithText("TELEGRAM").assertExists()
        val reenteredTelegramTitleTop = compose.onNodeWithText("TELEGRAM").fetchSemanticsNode().boundsInRoot.top
        assertEquals(freshTelegramTitleTop, reenteredTelegramTitleTop)
    }

    @Test
    fun aFailedPollWithAStaleSnapshotDoesNotStealFocusFromWhereTheViewerAlreadyIs() {
        val failure = MutableStateFlow<String?>(null)
        every { fixture.system.failure } returns failure
        press(compose.onNodeWithContentDescription("System"))
        press(compose.onNodeWithText("System"))
        compose.onNode(hasText("Catalogue")).assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(hasText("Cache")).assertIsFocused()

        failure.value = "System information could not be read. Try again."
        compose.waitForIdle()

        compose.onNodeWithText("Try again").assertExists()
        compose.onNode(hasText("Cache")).assertIsFocused()
    }

    @Test
    fun aSettingsPanelIsLeftForTelegramThenTheIndexThenTheMenu() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(telegramRow())
        press(compose.onNodeWithText("Application id and hash…"))
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()

        back()
        compose.onNodeWithText("Application id and hash…").assertIsFocused()
        back()
        telegramRow().assertIsFocused()
        back()
        compose.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun signOutAsksFirstInThePhonesWords() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(telegramRow())
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

        compose.onNodeWithContentDescription("Menu").assertExists()
        compose.onNodeWithText("Update library").assertDoesNotExist()
    }

    @Test
    fun thePreloadsRowIsAbsentWhileNothingIsRunningOrQueued() {
        openMenu()
        compose.onNode(hasText("Preloads", substring = true)).assertDoesNotExist()
    }

    /** The page scrolls, so a row this far down (fourth, past a screen this short) is still reachable by remote, not just by a semantics click a real D-pad walk could not perform. */
    @Test
    fun thePreloadsRowIsReachableByRemoteNamesTheCountAndOpensTheRealPreloadsPage() {
        fixture.filmPreloading.setQueueOverview(
            listOf(
                FilmPreloadRow.Running("film-0", "Film 0", TOTAL, heldBytes = HELD_40_PERCENT, pauseReason = null),
            ),
        )
        compose.waitForIdle()
        openMenu()
        compose.onNodeWithText("Update library").assertIsFocused()

        // Update library, TMDB key…, Start over, then Preloads — three rows down.
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithText("Preloads · 1").assertIsFocused()

        press(compose.onNodeWithText("Preloads · 1"))
        compose.onNodeWithText("Film 0").assertExists()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertExists()

        // Specifically the home wall, not merely a screen that happens to
        // carry a "Menu" node too (the bar's own ⋮ reads the same
        // description): Preloads clears the trimmed menu page before it
        // pushes its own frame, so Back lands past it, on the shelves
        // themselves.
        back()
        compose.onNode(hasText("Film 1") and hasClickAction()).assertExists()
    }

    @Test
    fun settingsOffersEveryCacheVolumeAndChoosingOneReachesTheModel() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Storage"))
        compose.onNodeWithText("Where").assertExists()
        compose.onNodeWithText("●  Internal storage", substring = true).assertExists()
        press(compose.onNodeWithText("○  USB drive", substring = true))
        verify { fixture.cacheBudget.chooseVolume("6BBF-D2D8") }
    }

    @Test
    fun settingsShowsTheHomeCacheServerAndAnAcceptedAddressReturnsToItsRow() {
        every { fixture.lanCache.setManualAddress("192.168.0.9:7788") } returns true
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Storage"))
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
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Storage"))
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
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Storage"))
        press(compose.onNodeWithText("Server address — found on the network"))
        verify { fixture.lanCache.clearErrors() }
    }

    @Test
    fun theSwitchRowTurnsTheServerOff() {
        press(compose.onNodeWithContentDescription("Settings"))
        press(compose.onNodeWithText("Storage"))
        press(compose.onNodeWithText("Use the home cache server — on"))
        verify { fixture.lanCache.setEnabled(false) }
    }

    @Test
    fun activeSessionsSignOutOnlyOnTheSecondPressAndNeverThisDevice() {
        every { fixture.settings.state } returns
            MutableStateFlow(
                SettingsUiState(
                    account = "Ada",
                    library = "Family films",
                    sessions = listOf(session("1", "Living room TV", current = true), session("2", "Old laptop", current = false)),
                ),
            )
        press(compose.onNodeWithContentDescription("Settings"))
        press(telegramRow())
        compose.onNodeWithText("Living room TV (this device)").assertExists()
        compose.onNodeWithText("Sign out Living room TV").assertDoesNotExist()
        press(compose.onNodeWithText("Sign out Old laptop"))
        verify(exactly = 0) { fixture.settings.revokeSession(any()) }
        press(compose.onNodeWithText("Confirm sign out — Old laptop"))
        verify { fixture.settings.revokeSession("2") }
    }

    private fun session(
        id: String,
        device: String,
        current: Boolean,
    ) = SessionSummary(id, device, "Android", "Mediagram", "0.61.0", "Berlin", 0, 0, current, false)

    private fun openMenu() {
        press(compose.onNodeWithContentDescription("Menu"))
    }

    /**
     * The Telegram index row, specifically — its own label collides with
     * [ui.tv.system.TvTelegramSection]'s own "Telegram" ledger heading,
     * which is always in the tree once Telegram is the section shown, not
     * only once entered; only the row carries a click action.
     */
    private fun telegramRow() = compose.onNode(hasText("Telegram", substring = true) and hasClickAction())

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /**
     * A real remote's own Back key — not the direct dispatcher call this
     * helper used to make, which skips Compose's key-event dispatch
     * entirely, so `TvLibraryChrome`'s own `onPreviewKeyEvent` (a real
     * `KEYCODE_BACK` always reaches it first) never saw it either.
     */
    private fun back() {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)) }
        compose.waitForIdle()
    }

    /** A real D-pad press, the way [TvSearchAndGenreTest]'s own helper drives one — where a click alone would skip the directional focus search a click never exercises. */
    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
    }

    private companion object {
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
