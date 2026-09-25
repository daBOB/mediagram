package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.CatalogUiState
import catalog.factsLine
import catalog.resumeLine
import catalog.shelvesOf
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.Watched
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.TvTheme
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertEquals

/**
 * What Robolectric can check about [TvCatalogScreen] without a real window
 * manager: which masthead entries, rows, plates and messages compose, and
 * where a press leads. Focus moving between the masthead and Home is real
 * window-manager behaviour and lives in `TvCatalogScreenTest` instead.
 *
 * Presses go through `SemanticsActions.OnClick` rather than
 * `performClick()`, which misbehaves against tv-material here (see
 * `TvAppTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvCatalogScreenStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    // Cleared as it is closed, so a test that closes one screen before
    // showing the next never has it closed a second time after it.
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    @Test
    fun theMastheadCarriesHomeTheShelvesTheThreeKeptEntriesAndTheViewer() {
        show(ready(films(2) + courses(1)))

        for (entry in listOf("Home", "Movies", "Tutorials", "Continue", "Watchlist", "Collections", "Ada")) {
            compose.onAllNodesWithText(entry).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "missing $entry" } }
        }
    }

    @Test
    fun choosingTheViewersNameReopensThePicker() {
        var reopened = 0
        show(ready(films(2)), onChoose = { reopened++ })

        compose.onNodeWithText("Ada").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, reopened)
    }

    @Test
    fun aHomeRowOfTenShowsSixPlatesAndSeeAll() {
        show(ready(films(10)))

        compose.onNodeWithText("Latest films · 10").assertExists()
        // Newest first: films 9 down to 4 are on the row, 3 down to 0 are not.
        (4..9).forEach { compose.onNodeWithText("Film $it").assertExists() }
        (0..3).forEach { compose.onNodeWithText("Film $it").assertDoesNotExist() }
        compose.onNodeWithText("See all").assertExists()
    }

    @Test
    fun seeAllSelectsThatRowsShelf() {
        show(ready(films(10)))

        compose.onNodeWithText("See all").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Latest films · 10").assertDoesNotExist()
        compose.onNodeWithText("Film 0").assertExists()
    }

    /** The phone's progress bar, as the words its Update item waits with. */
    @Test
    fun anArtworkFetchOrAChannelReadSaysSoAboveTheShelves() {
        show(ready(films(1)), fetching = true)
        compose.onNodeWithText("Fetching details and artwork…").assertExists()
        close()

        show(ready(films(1)).copy(refreshing = true))
        compose.onNodeWithText("Reading the channel…").assertExists()
        close()

        show(ready(films(1)))
        compose.onNodeWithText("Fetching details and artwork…").assertDoesNotExist()
        compose.onNodeWithText("Reading the channel…").assertDoesNotExist()
    }

    @Test
    fun homeLandsOnThePlateItIsToldWasOpened() {
        show(ready(films(3)), restoreKey = "film-0")

        compose.onNodeWithText("Film 0").assertIsFocused()
    }

    @Test
    fun coursesOnHomeAreLinesOfTextNotPlates() {
        show(ready(courses(1)))

        compose.onNodeWithText("Course 0 · 1 chapter").assertExists()
    }

    @Test
    fun aShelfWallOpensAFilmsTitleAndACollection() {
        var title: String? = null
        var collection: String? = null
        show(ready(films(1) + courses(1)), onOpenTitle = { title = it }, onOpenCollection = { collection = it })

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Film 0").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Tutorials").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Course 0").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("film-0", title)
        assertEquals("COURSE/Course 0", collection)
    }

    /**
     * Every shelf draws through the same wall; moving from one shelf to the
     * next still hands the remote down to the new wall's first plate rather
     * than leaving it on the tab.
     */
    @Test
    fun choosingAnotherShelfLandsOnItsFirstPlate() {
        val pilot = set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 5, episode = 1)
        show(ready(films(2) + pilot))
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("Film", substring = true) and isFocused()).assertExists()

        // Walked along to and pressed, as a remote does.
        compose.onNodeWithText("Series").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Series").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNode(hasText("A Show") and isFocused()).assertExists()
    }

    @Test
    fun continuePlatesSayWhereTheViewerStoppedAndNextUpSaysNextUp() {
        val episodes = (1..3).map { set("ep-$it", Kind.EPISODE, "Episode $it", show = "A Show", addedAt = 0, episode = it) }
        val film = films(1)
        val stopped = Progress(setId = "film-0", at = 1_200.0, duration = 6_000.0, updatedAt = 2)
        val watch =
            WatchSnapshot.Empty.copy(
                progress = listOf(stopped),
                watched = listOf(Watched(setId = "ep-1", finishedAt = 1)),
            )
        show(CatalogUiState.Ready(shelvesOf(film + episodes), watch = watch))

        compose.onNodeWithText(resumeLine(stopped)).assertExists()
        compose.onNodeWithText("Next up").assertExists()
    }

    @Test
    fun shelfPlatesCarryThePhonesCaptions() {
        show(ready(listOf(set("film-0", Kind.MOVIE, "A Film", addedAt = 0, year = 1999, durationSecs = 5_400)) + courses(1)))

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(factsLine(1999, 5_400)!!).assertExists()
        compose.onNodeWithText("Tutorials").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("1 chapter").assertExists()
    }

    @Test
    fun eachStateWithNoShelvesSaysThePhonesWordsUnderTheViewersName() {
        val messages =
            mapOf(
                CatalogUiState.Loading to "Loading your library…",
                CatalogUiState.Empty to "The library is empty.",
                CatalogUiState.KidsEmpty to "Nothing rated FSK 12 or under yet.",
                CatalogUiState.Failed("Could not reach the channel.") to "Could not reach the channel.",
            )
        for ((state, message) in messages) {
            show(state)
            compose.onNodeWithText(message).assertExists()
            compose.onNodeWithText("Ada").assertExists()
            compose.onNodeWithText("Home").assertDoesNotExist()
            close()
        }
    }

    /** Start over is behind Menu, and a library that cannot be read is exactly when it is needed. */
    @Test
    fun menuIsOfferedWithNoShelvesAndComingBackFromItLandsThere() {
        show(CatalogUiState.Failed("Could not reach the channel."), restoreKey = TvMenuEntryKey)

        compose.onNodeWithText("Menu").assertIsFocused()
    }

    private fun show(
        state: CatalogUiState,
        onChoose: () -> Unit = {},
        onOpenTitle: (String) -> Unit = {},
        onOpenCollection: (String) -> Unit = {},
        fetching: Boolean = false,
        restoreKey: String? = null,
    ) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvCatalogScreen(
                        state = state,
                        profile = TvChosenProfile(name = "Ada", onChoose = onChoose),
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onOpenList = {},
                        onCreateList = {},
                        fetching = fetching,
                        restoreKey = restoreKey,
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}

internal fun ready(sets: List<MediaSet>) = CatalogUiState.Ready(shelvesOf(sets))

/** [count] films, each newer than the last, so "latest" has an order to keep. */
internal fun films(count: Int) = (0 until count).map { set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()) }

internal fun courses(count: Int) =
    (0 until count).map { set("lesson-$it", Kind.TUTORIAL, "Lesson $it", show = "Course $it", addedAt = it.toLong()) }

internal fun set(
    id: String,
    kind: Kind,
    title: String,
    show: String? = null,
    addedAt: Long,
    episode: Int? = null,
    year: Int? = null,
    durationSecs: Int? = null,
) = MediaSet(
    setId = id,
    kind = kind,
    title = title,
    show = show,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = episode,
    episodeLast = episode,
    year = year,
    durationSecs = durationSecs,
    posterPath = null,
    totalBytes = 0,
    addedAt = addedAt,
    // The index's own title, as a real set carries it: the player's title
    // line reads this rather than the filled-in [MediaSet.title].
    rawTitle = title,
)
