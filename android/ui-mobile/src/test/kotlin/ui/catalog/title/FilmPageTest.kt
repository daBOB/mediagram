package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.Entry
import catalog.Shelf
import model.Credit
import model.Kind
import model.MediaSet
import model.Progress
import model.TitleCredits
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.TitleDetailScreen
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The film page's own gates: no Cast tab without a cast, no franchise link
 * without a franchise the library actually holds two films of, and the
 * pill naming a resume point only when there is one to resume — a Compose
 * port of the same rules `film-page.js`/`cast.js` follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilmPageTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun film(
        id: String = "film-1",
        collectionId: Long? = null,
        collectionName: String? = null,
    ): MediaSet =
        MediaSet(
            setId = id, kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = 10, collectionId = collectionId, collectionName = collectionName,
            posterKey = "tmdb-movie-$id",
        )

    @Test fun noCastTabWithoutACast() {
        show { TitleDetailScreen(film(), null, {}, onOpenGenre = {}) }
        assertTrue(compose.onAllNodesWithText("Cast").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aCastTabAppearsAndShowsCrewAndCastOnceCreditsArrive() {
        val credits =
            TitleCredits(
                cast = listOf(Credit(1, "Zendaya", "Chani", null)),
                crew = listOf(Credit(2, "Denis Villeneuve", "Director", null)),
            )
        show {
            TitleDetailScreen(film(), null, {}, onOpenGenre = {}, titleCredits = { credits })
        }
        compose.onNodeWithText("Cast").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Directed by Denis Villeneuve").performScrollTo().assertIsDisplayed()
        // Zendaya's own card sits in the Cast row's horizontal scroll, nested
        // inside the page's own vertical one — scrolling the outer page
        // brings the row into view but performScrollTo only ever drives the
        // nearest scrollable, which here is the horizontal one. Rendered at
        // all is what this asserts; on-screen is the outer scroll's job,
        // already exercised by the "Cast"/crew-line checks above.
        assertTrue(compose.onAllNodesWithText("Zendaya").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun noFranchiseLinkWithFewerThanTwoHeldFilms() {
        val shelves = listOf(Shelf("Movies", listOf(Entry.Film(film("a", collectionId = 7, collectionName = "Dune Franchise")))))
        show {
            TitleDetailScreen(film("a", collectionId = 7, collectionName = "Dune Franchise"), null, {}, onOpenGenre = {}, shelves = shelves)
        }
        assertTrue(compose.onAllNodesWithText("Part of").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aFranchiseLinkAppearsOnceTheLibraryHoldsTwoOfItsFilms() {
        val a = film("a", collectionId = 7, collectionName = "Dune Franchise")
        val b = film("b", collectionId = 7, collectionName = "Dune Franchise")
        val shelves = listOf(Shelf("Movies", listOf(Entry.Film(a), Entry.Film(b))))
        var openedFranchise: Long? = null
        show {
            TitleDetailScreen(a, null, {}, onOpenGenre = {}, shelves = shelves, onOpenFranchise = { openedFranchise = it })
        }
        compose.onNodeWithText("Dune Franchise").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(7L, openedFranchise)
    }

    @Test fun withNoResumePointThePillJustSaysPlay() {
        var played = false
        show { TitleDetailScreen(film(), null, { played = true }, onOpenGenre = {}) }
        compose.onNodeWithText("▶ Play").performClick()
        assertTrue(played)
    }

    @Test fun aMidwayPositionNamesItselfAResumePoint() {
        val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress(setId = "film-1", at = 600.0, duration = 9000.0, updatedAt = 1)))
        show { TitleDetailScreen(film(), null, {}, onOpenGenre = {}, watch = watch) }
        compose.onNodeWithText("▶ Resume from 10:00").assertIsDisplayed()
    }
}
