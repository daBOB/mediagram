package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.MoviesDepartment
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.catalog.HERO_ARTWORK_TEST_TAG

/**
 * The Artwork setting's Solid mode, at the two hero sites this module owns:
 * [TitleSpread] draws its own art and tagline quote directly, and
 * [MoviesDepartmentScreen] draws [DepartmentHero], which carries its own
 * lead's tagline as a quote inside itself. Both drop to text-only under
 * Solid and keep drawing under every other mode, [Backdrop.DEFAULT] here
 * standing in for the three that are not Solid.
 */
// A tall window: Robolectric's default one is short enough that the
// department screen's own LazyColumn clips its first item's hero to zero
// visible height — see ui.LibraryFlowTest's own note on the same window.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class HeroBackdropModesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(
        backdrop: Backdrop,
        content: @Composable () -> Unit,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    // The hero's own testTag lives on the AsyncImage inside DepartmentHero's
    // clickable Box, which merges its descendants' semantics into itself for
    // a screen reader — so finding the tag at all needs the unmerged tree,
    // the same reason TvPlateStateTest reaches for it on a plate's own tags.
    @Test
    fun solidDropsTheTitleSpreadsArtAndQuote() {
        show(Backdrop.SOLID) { titleSpread() }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(QUOTED_TAGLINE).assertDoesNotExist()
    }

    /** The quote is wide-only, like the web's `.spread-quote{display:none}` below 900px (`title-page.css:160`). */
    @Test
    fun defaultKeepsTheTitleSpreadsArtButNotItsQuoteOnACompactWidth() {
        show(Backdrop.DEFAULT) { titleSpread() }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(QUOTED_TAGLINE).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun defaultKeepsTheTitleSpreadsArtAndQuoteOnAWideWindow() {
        show(Backdrop.DEFAULT) { titleSpread() }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(QUOTED_TAGLINE).assertIsDisplayed()
    }

    @Test
    fun solidDropsTheDepartmentHeroesArtAndQuote() {
        show(Backdrop.SOLID) { moviesDepartmentScreen() }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(QUOTED_TAGLINE).assertDoesNotExist()
    }

    @Test
    fun defaultKeepsTheDepartmentHeroesArt() {
        // No quote assertion here: this class runs at a compact width
        // (w400dp), where the hero never draws the quote at all regardless
        // of backdrop mode — the web's own `.dept-quote{display:none}`
        // below 900px (`departments.css:91`), checked directly at every
        // width in `DepartmentHeroTest`.
        show(Backdrop.DEFAULT) { moviesDepartmentScreen() }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Composable
    private fun titleSpread() {
        TitleSpread(backdropPath = "backdrop.jpg", title = lead.title, facts = null, overview = null, tagline = lead.tagline)
    }

    @Composable
    private fun moviesDepartmentScreen() {
        MoviesDepartmentScreen(
            department = department,
            films = listOf(lead),
            watch = WatchSnapshot.Empty,
            onOpenTitle = {},
            onOpenGenre = {},
            onOpenGenresIndex = {},
            onOpenLatest = {},
            onSeeAllFilms = {},
            onPlay = {},
            titleInfo = { null },
        )
    }

    private val lead =
        MediaSet(
            "lead", Kind.MOVIE, "Held Film", null, null, null, null, null, null, 2020, 120, null, 10,
            backdropPath = "backdrop.jpg", tagline = "A tagline",
        )
    private val department =
        MoviesDepartment(filmCount = 1, hours = 0, lead = lead, featured = emptyList(), genres = emptyList(), acclaimed = emptyList(), recentlyAdded = emptyList())

    private companion object {
        const val QUOTED_TAGLINE = "“A tagline”"
    }
}
