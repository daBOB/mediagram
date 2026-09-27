package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.HERO_ARTWORK_TEST_TAG

/**
 * The cover's own actions and type, the parts a screenshot cannot check on
 * its own: the eyebrow and title are drawn uppercase regardless of the
 * film's own casing, Watch now plays rather than opening the title page,
 * and My List toggles and reads back its own state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class HomeCoverTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val film =
        MediaSet(
            setId = "the-film", kind = Kind.MOVIE, title = "a lowercase title", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", genres = listOf("drama"), rating = 7.5,
        )

    private fun show(
        backdrop: Backdrop = Backdrop.DEFAULT,
        watchlist: Set<String> = emptySet(),
        onPlay: (MediaSet) -> Unit = {},
        onOpenTitle: (String) -> Unit = {},
        onToggleWatchlist: (String, Boolean) -> Unit = { _, _ -> },
        content: @Composable () -> Unit = {
            HomeCover(
                films = listOf(film), watchlist = watchlist, width = 1164.dp,
                onPlay = onPlay, onOpenTitle = onOpenTitle, onToggleWatchlist = onToggleWatchlist,
            )
        },
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme { CompositionLocalProvider(LocalBackdrop provides backdrop) { content() } }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theEyebrowAndTitleDrawUppercaseRegardlessOfTheFilmsOwnCasing() {
        show()
        compose.onNodeWithText("FEATURED TODAY · DRAMA").assertIsDisplayed()
        compose.onNodeWithText("A LOWERCASE TITLE").assertIsDisplayed()
    }

    @Test
    fun watchNowPlaysRatherThanOpeningTheTitle() {
        var played: String? = null
        var opened: String? = null
        show(onPlay = { played = it.setId }, onOpenTitle = { opened = it })
        compose.onNodeWithContentDescription("Watch now: ${film.title}").performClick()
        kotlin.test.assertEquals("the-film", played)
        kotlin.test.assertEquals(null, opened)
    }

    @Test
    fun myListTogglesOnThenReadsBackChecked() {
        var listed: Boolean? = null
        show(watchlist = emptySet(), onToggleWatchlist = { _, on -> listed = on })
        compose.onNodeWithContentDescription("Add to My List").performClick()
        kotlin.test.assertEquals(true, listed)

        // Recomposed with the set now on the list — the pill reads the toggle back.
        show(watchlist = setOf("the-film"))
        compose.onNodeWithText("✓ My List").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove from My List").assertIsDisplayed()
    }

    @Test
    fun solidLeavesTheCoversOwnArtworkShowingUnlikeTheTitleSpreadOrADepartmentHero() {
        // `appearance.css:22` lists `.cover-stage` on the *blurred* rule only
        // — the *solid* rule two lines below (`appearance.css:24`) names
        // just `.spread-art`/`.dept-art`. The web's own Solid mode leaves
        // the cover's picture showing; only Blurred changes it.
        show(backdrop = Backdrop.SOLID)
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun blurredAlsoLeavesTheCoversArtworkDrawn() {
        show(backdrop = Backdrop.BLURRED)
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theLineupShrinkingPastTheShownPageDoesNotCrash() {
        // The regression: a `Crossfade` keyed on an index still reads
        // `films[shown]` through the same lambda once `films` has shrunk,
        // so the outgoing slide during a fade can ask for an index past the
        // new list's end — pinning an editor's choice, marking a cover film
        // watched on another device, or a library refresh can all shrink
        // the pool while a viewer sits on its last slide. Keying on the
        // film itself is what this guards.
        val threeFilms =
            listOf(
                film.copy(setId = "a", title = "Film A"),
                film.copy(setId = "b", title = "Film B"),
                film.copy(setId = "c", title = "Film C"),
            )
        val films = mutableStateOf(threeFilms)
        show(
            content = {
                HomeCover(films = films.value, watchlist = emptySet(), width = 1164.dp, onPlay = {}, onOpenTitle = {}, onToggleWatchlist = { _, _ -> })
            },
        )
        compose.onNodeWithContentDescription("Cover story 3 of 3").performClick()
        compose.waitForIdle()

        compose.runOnUiThread { films.value = threeFilms.take(2) }
        compose.waitForIdle()

        // No crash, and the page clamps to the new last film rather than
        // staying on the one that no longer exists.
        compose.onNodeWithText("FILM B").assertIsDisplayed()
    }
}
