package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
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
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag

/**
 * Back from whatever a Home card opened lands on that card, band by band —
 * never on the cover's Watch now, Home's arrival with nothing to return to,
 * nor on the bar's Home pill.
 *
 * The cover is drawn above every band here (films with a backdrop *and* a
 * poster, which the cover's own pick needs), so the cover is the list's first
 * section and every other band is a return that is not also where a fresh
 * arrival would land anyway. Without a cover the list's first section was the
 * restored band itself, which is how a return that went to the cover instead
 * hid from these walks. The Editor's choice is the lead card, focused with the
 * cover still half in view, so its return composes the cover again — the one
 * the list's restorer redirected to the cover and, on the box, the bar's pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeReturnTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture =
                TvAppFixture(
                    TvSetupStage.READY,
                    listOf(Profile(id = "ada", name = "Ada")),
                    "ada",
                    library(),
                    watch = {
                        setEditorsChoice("film-0", true)
                        setProgress(UNDERWAY, 600.0, 6_000.0)
                    },
                )
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(EditorsChoice).fetchSemanticsNodes().isNotEmpty() }
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

    /** The cover's own stop is its Watch now, on the film whose Details were opened — not the cover's first film. */
    @Test
    fun backFromTheCoversDetailsLandsOnThatCoverStory() {
        val watchNow = compose.onNode(hasContentDescription("Watch now: ", substring = true))
        val story = watchNow.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

        // The title page draws a Details of its own, so Home's Watch now is what shows Home has gone.
        openAndComeBack(hasText("Details") and hasClickAction(), gone = hasContentDescription(story))

        compose.onNode(hasContentDescription(story)).assertIsFocused()
    }

    @Test
    fun backFromTheEditorsChoiceLandsOnItsCard() = assertReturnsTo(EditorsChoice)

    @Test
    fun backFromTheTrendingCardLandsOnIt() = assertReturnsTo(hasText("TRENDING ON TMDB") and hasClickAction())

    @Test
    fun backFromTheBestRatedCardLandsOnIt() = assertReturnsTo(hasText("BEST-RATED IN THE LIBRARY") and hasClickAction())

    /** A resume card plays rather than opening a page; Back puts the player's controls away, then leaves it. */
    @Test
    fun backFromTheContinueCardsPlayerLandsOnTheCard() {
        val card = hasText("Film 3") and hasClickAction()
        openAndComeBack(card) {
            compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
            back() // the controls
            back() // the player
        }

        compose.onNode(card).assertIsFocused()
    }

    /** The newest film, which a cover story may carry too: the band it was opened from wins. */
    @Test
    fun backFromARecentlyAddedPosterLandsOnIt() = assertReturnsTo(hasContentDescription("Film 11") and hasClickAction())

    @Test
    fun backFromALatestSeriesPosterLandsOnIt() = assertReturnsTo(hasText("A Show") and hasClickAction())

    @Test
    fun backFromALatestCourseLandsOnIt() = assertReturnsTo(hasText("A Course") and hasClickAction())

    /** The same walk the way the box takes it: out of touch mode, the remote's own keys, Down from the cover onto the feature under it. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theRemotesOwnWalkBackFromTheEditorsChoiceLandsOnItsCard() {
        compose.runOnUiThread { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
        val card = compose.onNode(EditorsChoice)
        repeat(4) { if (!card.isFocused()) key(KeyEvent.KEYCODE_DPAD_DOWN) }
        card.assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onAllNodes(EditorsChoice).assertCountEquals(0)

        key(KeyEvent.KEYCODE_BACK)

        card.assertIsFocused()
    }

    private fun assertReturnsTo(card: SemanticsMatcher) {
        openAndComeBack(card)
        compose.onNode(card).assertIsFocused()
    }

    /** Focuses [card] where Home draws it, opens it, proves Home is gone by [gone], then leaves whatever opened by [leave]. */
    private fun openAndComeBack(
        card: SemanticsMatcher,
        gone: SemanticsMatcher = card,
        leave: () -> Unit = ::back,
    ) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(card)
        val node = compose.onNode(card)
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        node.assertIsFocused()
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onAllNodes(gone).assertCountEquals(0)

        leave()
    }

    private fun SemanticsNodeInteraction.isFocused() = fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private companion object {
        val EditorsChoice = hasText("EDITOR'S CHOICE") and hasClickAction()

        /** The one film with a position, so it is the one card Continue draws. */
        const val UNDERWAY = "film-3"

        /**
         * Twelve films with art enough for the cover — film-1 the only one with
         * a popularity (Trending), film-2 the only one rated (Best-rated) — a
         * show and a course, so every band Home draws has a card to open.
         */
        fun library() =
            (0 until 12).map { index ->
                set("film-$index", Kind.MOVIE, "Film $index", addedAt = index.toLong()).copy(
                    backdropPath = "/nowhere/backdrop-$index.jpg",
                    posterPath = "/nowhere/poster-$index.jpg",
                    popularity = 50.0.takeIf { index == 1 },
                    rating = 8.0.takeIf { index == 2 },
                )
            } +
                listOf(
                    set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 20, episode = 1).copy(season = 1),
                    set("lesson-1", Kind.TUTORIAL, "Lesson 1", show = "A Course", addedAt = 21, episode = 1),
                )
    }
}
