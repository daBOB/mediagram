package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.MediaSet
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
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag
import ui.tv.player.TvSeekBarTag

/**
 * [TvLibrary] walked the way a remote walks it, over the real
 * `CatalogViewModel` [TvAppFixture] builds: open, go deeper, press Back,
 * and land where the walk came from — on the very plate or row that was
 * pressed, which is the part only a television has to get right.
 *
 * Home's first row is "Latest films", so the remote starts on a film, not
 * on the show; landing back on the show's plate proves Back restored it
 * rather than simply refocusing Home's first stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2) + show())
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
    fun backFromAnEpisodeWalksBackToTheHomePlateThatOpenedTheShow() {
        compose.onNodeWithText("Film 1").assertIsFocused()

        press(plate("A Show"))
        // The show's own Play takes the remote, its first season listed under the picker.
        compose.onNodeWithText("▶ Play S1 E1").assertIsFocused()
        // An episode row plays, as on the phone and the web; no title page between.
        press(compose.onNodeWithText("1. Pilot"))
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()

        back() // the controls
        back() // the player
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        back()
        plate("A Show").assertIsFocused()
    }

    /** The season picked survives a refresh, and Back leaves the show from wherever its picker was left. */
    @Test
    fun aCatalogRefreshMidWalkKeepsThePositionAndTheWayBack() {
        press(plate("A Show"))
        compose.onNodeWithText("Season 2 · one episode").performSemanticsAction(SemanticsActions.RequestFocus)
        press(compose.onNodeWithText("Season 2 · one episode"))
        compose.onNodeWithText("1. Return").assertExists()

        compose.runOnUiThread { fixture.catalog.reload() }
        compose.waitForIdle()

        compose.onNodeWithText("1. Return").assertExists()
        compose.onNodeWithText("Season 2 · one episode").assertIsFocused()
        back()
        plate("A Show").assertIsFocused()
    }

    @Test
    fun playOpensThePlayerAndBackPutsItsControlsAwayBeforeReturningToTheTitle() {
        press(plate("Film 1"))
        press(compose.onNodeWithText("▶ Play"))
        compose.onNodeWithTag(TvSeekBarTag).assertExists()

        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()

        back()
        // Leaving saved where the player stood, so the pill now resumes there.
        compose.onNodeWithText("▶ Resume from 0:42").assertIsFocused()
        back()
        // Film 1 is on Continue now too; the remote is back on the plate that opened it.
        compose.onNode(hasText("Film 1") and hasClickAction() and isFocused()).assertExists()
        compose.onNode(hasText("Film 1") and hasContentDescription("0:42", substring = true)).assertIsNotFocused()
    }

    /**
     * An episode plays into its show, as on the phone: Next moves the player
     * on to the show's next episode in place, so Back still leaves to the
     * episode's page, not to the episode that was left.
     */
    @Test
    fun anEpisodePlaysIntoItsShowAndNextMovesOnInPlace() {
        press(plate("A Show"))
        press(compose.onNodeWithText("1. Pilot"))
        compose.onNodeWithText("Pilot", substring = true).assertExists()

        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)) }
        compose.waitForIdle()

        compose.onNodeWithText("Return", substring = true).assertExists()
        back()
        back()
        compose.onNodeWithText("1. Pilot").assertIsFocused()
    }

    /**
     * The pill-press rule: pressing a pill keeps the remote on it rather
     * than jumping straight to a plate, and Down is what steps it into the
     * page's own first stop — Movies' own second plate here, since Film 1
     * is Home's first but not this shelf's.
     */
    @Test
    fun choosingAnotherPillKeepsTheRemoteOnItUntilDownEntersItsFirstPlate() {
        press(plate("Film 1"))
        back()
        plate("Film 1").assertIsFocused()

        // A semantics click alone never moves focus the way a real remote's
        // centre press does — focused first, as "Series" already is a few
        // lines down in `TvCatalogScreenStateTest`'s own equivalent walk.
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Movies").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)

        // Movies' own department front page can carry "Film 0" under more
        // than one heading with only two films in the shelf (Featured and
        // Recently added both) — `isFocused()` alone is still unambiguous:
        // only one of the two is ever the remote's own stop.
        compose.onNode(hasText("Film 0") and hasClickAction() and isFocused()).assertExists()
    }

    @Test
    fun backAtTheCatalogRootGoesUpThroughThePillThenTheRailBeforeTheAppFinishes() {
        compose.onNodeWithText("Film 1").assertIsFocused()

        back()
        compose.onNodeWithText("Home").assertIsFocused()

        back()
        // The rail is open now that the remote actually landed on it, so
        // its row reads by its visible label rather than by the content
        // description a collapsed row falls back to.
        compose.onNodeWithText("My List").assertIsFocused()
        compose.runOnUiThread {
            val dispatcher = controller.get().onBackPressedDispatcher
            kotlin.test.assertFalse(dispatcher.hasEnabledCallbacks(), "a third Back is left to close the app")
        }
    }

    private fun plate(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** One show of two seasons, one episode each — enough for a season picker over its list. */
    private fun show(): List<MediaSet> =
        listOf(
            set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 10, episode = 1).copy(season = 1),
            set("return", Kind.EPISODE, "Return", show = "A Show", addedAt = 11, episode = 1).copy(season = 2),
        )
}
