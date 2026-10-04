package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
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
import ui.catalog.CollectionScreen
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The series page's own gates: the pill names a real resume point (or
 * nothing to resume at all), no Cast tab without a cast, and the tab and
 * season a viewer chose survive a watch-state update — the same finding
 * [TitleTabsTest] covers for a film page, carried to the season picker too.
 */
// A tall window: the series page is a LazyColumn, and Robolectric's default
// is short enough that its own episode rows sit past the prefetch window
// and are never composed at all — nothing a scroll action can fix, since
// there is nothing yet to scroll to.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class SeriesPageTest {
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

    private fun ep(season: Int, episode: Int): MediaSet =
        MediaSet(
            setId = "s${season}e$episode", kind = Kind.EPISODE, title = "Ep $season.$episode", show = "Show",
            chapter = null, path = null, season = season, episodeFirst = episode, episodeLast = null,
            year = 2020, durationSecs = 1800, posterPath = null, totalBytes = 10,
        )

    private fun oneSeasonShow(): Entry.Collection =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 1, chapters = 1, divisions = listOf(Division("Season 1", 1, listOf(ep(1, 1)), emptyList())),
        )

    private fun twoSeasonShow(): Entry.Collection =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 2, chapters = 2,
            divisions = listOf(
                Division("Season 1", 1, listOf(ep(1, 1)), emptyList()),
                Division("Season 2", 2, listOf(ep(2, 1)), emptyList()),
            ),
        )

    /**
     * Wires `season`/`onSelectSeason` the way a real screen does — a saved
     * choice fed back in, not this composable's own state — so a test that
     * clicks the picker exercises the same round trip
     * [ui.LibraryPositions.setCollectionSeason] does, rather than a
     * component-local `remember` this file would be the only caller of.
     */
    private fun renderCollection(
        collection: Entry.Collection,
        watch: WatchSnapshot = WatchSnapshot.Empty,
        titleCredits: suspend (String) -> TitleCredits = { TitleCredits.Empty },
        season: String? = null,
        info: TitleInfo? = null,
        onOpenPerson: (Long) -> Unit = {},
    ) {
        var chosenSeason by mutableStateOf(season)
        show {
            CollectionScreen(
                collection = collection, info = info, watch = watch, heldIds = emptySet(),
                onOpenTitle = {}, onOpenGenre = {},
                onOpenPerson = onOpenPerson,
                titleCredits = titleCredits,
                season = chosenSeason,
                onSelectSeason = { chosenSeason = it },
            )
        }
    }

    /** What the provider says about the whole show: when it ran and how much of it exists. */
    private val aired =
        TitleInfo(
            overview = null, tagline = null, genres = null, rating = null, network = null, status = null,
            firstAir = "2011-04-17", lastAir = "2019-05-19", totalSeasons = 8u, totalEpisodes = 73u,
        )

    /** `series-page.js` spells the seasons — "two seasons", not "2 seasons". */
    @Test fun theFactsLineSpellsItsSeasons() {
        renderCollection(twoSeasonShow())
        compose.onNodeWithText("2020 · two seasons").assertIsDisplayed()
    }

    /** The provider's air dates say when the show ran, not only when the copies held are from. */
    @Test fun theFactsLineAndAboutFollowTheProvidersAirDatesAndTotals() {
        renderCollection(twoSeasonShow(), info = aired)
        compose.onNodeWithText("2011–2019 · two seasons").assertIsDisplayed()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("2011–2019").assertIsDisplayed()
        compose.onNodeWithText("2 of 73 episodes · 2 of 8 seasons", substring = true).assertIsDisplayed()
    }

    @Test fun aboutNamesTheAudioLanguagesTheEpisodesCarry() {
        val show = oneSeasonShow().let { it.copy(divisions = listOf(Division("Season 1", 1, listOf(ep(1, 1).copy(alang = listOf("ja", "en"))), emptyList()))) }
        renderCollection(show)
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Audio").assertIsDisplayed()
        compose.onNodeWithText("Japanese, English").assertIsDisplayed()
    }

    @Test fun aCreatorsNameOpensTheirPage() {
        val credits = TitleCredits(
            cast = listOf(Credit(2, "Bryan Cranston", "Walter White", null)),
            crew = listOf(Credit(1, "Vince Gilligan", "Creator", null)),
        )
        var opened: Long? = null
        renderCollection(oneSeasonShow(), titleCredits = { credits }, onOpenPerson = { opened = it })
        compose.onNodeWithText("Cast").performClick()
        compose.onNodeWithText("Created by Vince Gilligan").performFirstLinkClick()
        assertEquals(1L, opened)
    }

    @Test fun aFreshShowOffersToPlayItsFirstEpisode() {
        renderCollection(oneSeasonShow())
        compose.onNodeWithText("▶ Play S1 E1").assertIsDisplayed()
    }

    @Test fun aMidwayPositionNamesItselfAResumePoint() {
        val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress(setId = "s1e1", at = 600.0, duration = 1800.0, updatedAt = 1)))
        renderCollection(oneSeasonShow(), watch = watch)
        compose.onNodeWithText("▶ Resume S1 E1").assertIsDisplayed()
    }

    @Test fun noCastTabWithoutACast() {
        renderCollection(oneSeasonShow())
        assertTrue(compose.onAllNodesWithText("Cast").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aCastTabAppearsOnceCreditsNameACreator() {
        val credits = TitleCredits(
            cast = listOf(Credit(2, "Bryan Cranston", "Walter White", null)),
            crew = listOf(Credit(1, "Vince Gilligan", "Creator", null)),
        )
        renderCollection(oneSeasonShow(), titleCredits = { credits })
        compose.onNodeWithText("Cast").assertIsDisplayed().performClick()
        compose.onNodeWithText("Created by Vince Gilligan").assertIsDisplayed()
    }

    @Test fun theSeasonPickerSwitchesWhichEpisodesShow() {
        renderCollection(twoSeasonShow())
        compose.onNodeWithText("Ep 1.1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Season 1 · one episode").performClick()
        compose.onNodeWithText("Season 2 · one episode").performClick()
        compose.onNodeWithText("Ep 2.1", substring = true).assertIsDisplayed()
    }

    /**
     * A season already chosen (what [ui.LibraryPositions.collectionSeason]
     * hands back after a title opened from this page and left again) is
     * shown as-is — the page never falls back to its own default once a
     * caller names one.
     */
    @Test fun aSeasonHandedInByTheCallerIsShownWithoutTouchingThePicker() {
        renderCollection(twoSeasonShow(), season = "Season 2")
        compose.onNodeWithText("Ep 2.1", substring = true).assertIsDisplayed()
    }

    /**
     * The default season used to be picked once, on first composition, and
     * never revisited — a resume point that arrived afterwards (the common
     * case: `watch` loads asynchronously) was computed but never actually
     * used to choose which season opens.
     */
    @Test fun theDefaultSeasonPicksUpAResumePointThatArrivesAfterFirstComposition() {
        var watch by mutableStateOf(WatchSnapshot.Empty)
        show {
            CollectionScreen(
                collection = twoSeasonShow(), info = null, watch = watch, heldIds = emptySet(),
                onOpenTitle = {}, onOpenGenre = {},
            )
        }
        compose.onNodeWithText("Ep 1.1", substring = true).assertIsDisplayed()

        compose.runOnUiThread {
            watch = WatchSnapshot.Empty.copy(progress = listOf(Progress(setId = "s2e1", at = 600.0, duration = 1800.0, updatedAt = 1)))
        }
        compose.waitForIdle()

        compose.onNodeWithText("Ep 2.1", substring = true).assertIsDisplayed()
    }

    @Test fun tabSelectionSurvivesAWatchStateUpdate() {
        val credits = TitleCredits(
            cast = listOf(Credit(2, "Bryan Cranston", "Walter White", null)),
            crew = listOf(Credit(1, "Vince Gilligan", "Creator", null)),
        )
        var watch by mutableStateOf(WatchSnapshot.Empty)
        show {
            CollectionScreen(
                collection = oneSeasonShow(), info = null, watch = watch, heldIds = emptySet(),
                onOpenTitle = {}, onOpenGenre = {},
                titleCredits = { credits },
            )
        }
        compose.onNodeWithText("Cast").performClick()
        compose.onNodeWithText("Created by Vince Gilligan").assertIsDisplayed()

        compose.runOnUiThread { watch = watch.copy(watchlist = listOf("s1e1")) }
        compose.waitForIdle()
        compose.onNodeWithText("Created by Vince Gilligan").assertIsDisplayed()
    }
}
