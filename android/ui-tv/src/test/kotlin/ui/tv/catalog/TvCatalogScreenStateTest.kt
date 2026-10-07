package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
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
import kotlin.test.assertTrue

/**
 * What Robolectric can check about [TvCatalogScreen] without a real window
 * manager: which bar pills, rows, plates and messages compose, and where a
 * press leads. Focus moving between the bar and Home is real window-manager
 * behaviour and lives in `TvCatalogScreenTest` instead.
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

    /**
     * `mastheadTabsOf`: the bar's own pill row is departments only — Home,
     * the shelves, Collections — and Continue/My List are the rail's own two
     * kept rows, never pills.
     */
    @Test
    fun theBarCarriesHomeTheShelvesCollectionsAndTheViewerButNotContinueOrWatchlist() {
        show(ready(films(2) + courses(1)))

        for (entry in listOf("Home", "Movies", "Tutorials", "Collections")) {
            compose.onAllNodesWithText(entry).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "missing $entry" } }
        }
        compose.onNodeWithContentDescription("Who's watching: Ada").assertExists()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("My List").assertDoesNotExist()
    }

    @Test
    fun choosingTheViewersAvatarReopensThePicker() {
        var reopened = 0
        show(ready(films(2)), onChoose = { reopened++ })

        compose.onNodeWithContentDescription("Who's watching: Ada").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, reopened)
    }

    /** The magazine layout's own "Recently added" replaces the plain grid's "Latest films" on Home — see `TvHome`'s own doc. */
    @Test
    fun aHomeRowOfTenShowsEightPlatesAndSeeAll() {
        show(ready(films(10)))

        compose.onNodeWithText("Recently Added · 10").assertExists()
        // Newest first, at the magazine layout's own eight-wide poster
        // limit: films 9 down to 2 are on the row, 1 and 0 are not.
        (2..9).forEach { compose.onNodeWithText("Film $it").assertExists() }
        (0..1).forEach { compose.onNodeWithText("Film $it").assertDoesNotExist() }
        compose.onNodeWithText("See all").assertExists()
    }

    @Test
    fun seeAllSelectsThatRowsShelf() {
        show(ready(films(10)))

        compose.onNodeWithText("See all").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Recently Added · 10").assertDoesNotExist()
        compose.onAllNodesWithText("Film 0").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Film 0 missing from the Movies shelf" } }
    }

    /** [catalog.EditorialPicks]' own cover and features draw on Home once the library is large enough to feature something. */
    @Test
    fun aLibraryWithBackdropsShowsTheCoverStoryAndFeatureCards() {
        val featured = (0 until 4).map { set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()).copy(backdropPath = "/bd$it", posterPath = "/p$it") }
        show(ready(featured))

        compose.onAllNodesWithText("FEATURED TODAY", substring = true).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "no cover story" } }
        compose.onAllNodesWithText("Watch now", substring = true).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "no Watch now on the cover" } }
    }

    /**
     * Past a dozen films, Movies gets its own department front page instead
     * of the plain wall. The real TV viewport: the department hero is a
     * fixed 360dp regardless of whether it has a lead, tall enough that a
     * short default Robolectric screen never scrolls the wall's own cache
     * window far enough to compose the "All N films" link below it.
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun aLargeMoviesShelfGetsItsOwnDepartmentFrontPage() {
        val films = (0 until 20).map { set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()) }
        show(ready(films))

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        // Lazy now, unlike the `Column` + `verticalScroll` this page used to
        // be: a real remote's Down scrolls the link into view the same way.
        compose.onNode(hasTestTag(TvMoviesDepartmentPageTestTag)).performScrollToNode(hasText("All 20 films →"))
        compose.onNodeWithText("All 20 films →").assertExists()
    }

    /** Collections is a department tab now, not a kept tab — franchises beside the household's own lists. */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun collectionsShowsFranchisesAndLists() {
        val a1 = set("a1", Kind.MOVIE, "Adventure One", addedAt = 0).copy(collectionId = 9L, collectionName = "Adventure Saga")
        val a2 = set("a2", Kind.MOVIE, "Adventure Two", addedAt = 1).copy(collectionId = 9L, collectionName = "Adventure Saga")
        show(ready(listOf(a1, a2)))

        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.OnClick)
        // A destination card sets its name in capitals, as `.destination-name` does.
        compose.onNodeWithText("ADVENTURE SAGA").assertExists()
        // Arrival focuses the franchise row, same as a real remote's Down
        // from the pill; "Your lists" sits below it, off the first screen at
        // this fixed TV height, so a real remote's own Down scrolls it into
        // view the same way this test does.
        compose.onNode(hasTestTag(TvCollectionsPageTestTag)).performScrollToNode(hasText("Your lists"))
        compose.onNodeWithText("Your lists").assertExists()
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

    /**
     * A poster opened from a section below Home's own first one, and left
     * again, must restore focus to that poster — not fall through to a
     * pill in the bar, which is what a request fired before the outer
     * `LazyColumn` had actually scrolled that section into its own
     * composition once did. "Latest series" sits below "Recently added"
     * here (`films(10)`'s own eight posters, plus a show), so restoring to
     * it genuinely exercises the scroll, not just a first-item lookup.
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun homeRestoresFocusToAPosterBelowTheFirstSectionRatherThanAPill() {
        val episodes = (1..3).map { set("ep-$it", Kind.EPISODE, "Episode $it", show = "A Show", addedAt = 0, episode = it) }
        var openedCollection: String? = null
        show(ready(films(10) + episodes), onOpenCollection = { openedCollection = it })

        compose.onNodeWithText("A Show").performSemanticsAction(SemanticsActions.OnClick)
        val collectionKey = requireNotNull(openedCollection) { "clicking the series poster never opened a collection" }
        close()

        show(ready(films(10) + episodes), restoreKey = collectionKey)

        compose.onNodeWithText("A Show").assertIsFocused()
        compose.onNodeWithText("Movies").assertIsNotFocused()
        compose.onNodeWithText("Series").assertIsNotFocused()
    }

    /** Home's Latest courses holds six, as the web's Home does: a course row is a list and keeps the shorter row limit, not the poster rows' eight. */
    @Test
    fun homesLatestCoursesHoldsTheNewestSix() {
        show(ready(courses(8)))

        (2..7).forEach { compose.onNodeWithText("Course $it").assertExists() }
        (0..1).forEach { compose.onNodeWithText("Course $it").assertDoesNotExist() }
    }

    /**
     * The chosen tab is kept by its department, not by its position: a
     * refresh that brings Movies in ahead of Tutorials must leave Tutorials
     * on screen, not whichever department now sits where it used to.
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun aRefreshThatAddsADepartmentAheadKeepsTheChosenOneOnScreen() {
        val state = mutableStateOf<CatalogUiState>(ready(courses(1)))
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvCatalogScreen(
                        state = state.value,
                        profile = TvChosenProfile(name = "Ada", onChoose = {}),
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onOpenList = {},
                        onCreateList = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Tutorials").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Course 0").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Tutorials never opened" } }

        compose.runOnUiThread { state.value = ready(films(2) + courses(1)) }
        compose.waitForIdle()

        compose.onAllNodesWithText("Course 0").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "the refresh moved the screen off Tutorials" } }
        compose.onNodeWithText("No documentaries yet", substring = true).assertDoesNotExist()
    }

    @Test
    fun coursesOnHomeAreLinesOfTextNotPlates() {
        show(ready(courses(1)))

        compose.onNodeWithText("Course 0").assertExists()
        // Spelled, the same rule the web's own `collectionGrid` counts by —
        // "one lesson · one chapter", not "1 lesson · 1 chapter".
        compose.onNodeWithText("one lesson · one chapter").assertExists()
    }

    /** The real TV viewport — [aLargeMoviesShelfGetsItsOwnDepartmentFrontPage]'s own doc on why the department pages need it now. */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun aShelfWallOpensAFilmsTitleAndACollection() {
        var title: String? = null
        var collection: String? = null
        show(ready(films(1) + courses(1)), onOpenTitle = { title = it }, onOpenCollection = { collection = it })

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        // The one film sits under both Featured and Recently added on its own
        // department front page — either presses the same title, so the first
        // found is as good as any.
        compose.onAllNodesWithText("Film 0")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Tutorials").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Course 0").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("film-0", title)
        assertEquals("COURSE/Course 0", collection)
    }

    /**
     * The plan's own pill-press rule: choosing another pill swaps the wall
     * shown but keeps the remote on the pill itself, rather than leaving it
     * on whatever plate the previous pill's wall happened to focus — Down
     * is what steps it into the new wall's first plate, which is real
     * window-manager focus search and lives in `TvCatalogScreenTest` instead.
     */
    /** The real TV viewport — [aLargeMoviesShelfGetsItsOwnDepartmentFrontPage]'s own doc on why the department pages need it now. */
    @Config(qualifiers = "w960dp-h540dp")
    @Test
    fun choosingAnotherPillKeepsTheRemoteOnItRatherThanOnThePreviousWallsPlate() {
        val pilot = set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 5, episode = 1)
        show(ready(films(2) + pilot))
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Movies").assertIsFocused()
        compose.onAllNodesWithText("Film 0").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Movies' own wall never composed" } }

        // Walked along to and pressed, as a remote does.
        compose.onNodeWithText("Series").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Series").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Series").assertIsFocused()
        compose.onAllNodesWithText("A Show").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Series' own wall never composed" } }
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

        // The magazine band's own resume cards hold this line back from
        // view the same way the phone's own `ResumeCard` does (the progress
        // bar already draws it) — said only for TalkBack, on the card's
        // own content description.
        compose.onNodeWithContentDescription(resumeLine(stopped)).assertExists()
        compose.onNodeWithContentDescription("Next up").assertExists()
    }

    /** The real TV viewport — [aLargeMoviesShelfGetsItsOwnDepartmentFrontPage]'s own doc on why the department pages need it now. */
    @Config(qualifiers = "w960dp-h540dp")
    @Test
    fun shelfPlatesCarryThePhonesCaptions() {
        show(ready(listOf(set("film-0", Kind.MOVIE, "A Film", addedAt = 0, year = 1999, durationSecs = 5_400)) + courses(1)))

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        // Duplicated across Featured and Recently added on the one-film
        // department front page — "it is offered" is what this checks, not
        // "exactly once".
        compose.onAllNodesWithText(factsLine(1999, 5_400)!!).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "caption missing" } }
        compose.onNodeWithText("Tutorials").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("1 chapter").assertExists()
    }

    @Test
    fun eachStateWithNoShelvesSaysThePhonesWordsUnderTheViewersName() {
        val messages =
            mapOf(
                CatalogUiState.Loading to "Loading your library…",
                CatalogUiState.Empty to "The library is empty.",
                CatalogUiState.KidsEmpty(6) to "Nothing rated FSK 6 or under yet.",
                CatalogUiState.Failed("Could not reach the channel.") to "Could not reach the channel.",
            )
        for ((state, message) in messages) {
            show(state)
            compose.onNodeWithText(message).assertExists()
            compose.onNodeWithContentDescription("Who's watching: Ada").assertExists()
            compose.onNodeWithText("Home").assertDoesNotExist()
            close()
        }
    }

    /** Start over is behind ⋮, and a library that cannot be read is exactly when it is needed. */
    @Test
    fun menuIsOfferedWithNoShelvesAndComingBackFromItLandsThere() {
        show(CatalogUiState.Failed("Could not reach the channel."), restoreKey = TvMenuEntryKey)

        compose.onNodeWithContentDescription("Menu").assertIsFocused()
    }

    /**
     * A department page must not take the remote back from Search: `TvPage`'s
     * own default (`takesArrivalFocus = true`) would otherwise override the
     * catalogue's `LocalTakesArrivalFocus provides !backToMasthead` the
     * moment a department page composes, stealing the remote onto one of its
     * own plates the instant Back from Search lands here.
     */
    @Test
    fun aDepartmentPageDoesNotStealFocusFromSearchOnBackFromIt() {
        val restoreKey = mutableStateOf<String?>(null)
        val films = (0 until 15).map { set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()) }
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvCatalogScreen(
                        state = ready(films),
                        profile = TvChosenProfile(name = "Ada", onChoose = {}),
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onOpenList = {},
                        onCreateList = {},
                        restoreKey = restoreKey.value,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Film 0").fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "department page never opened" } }

        // Search opened from the bar, then Back — the catalogue's own
        // sentinel for it.
        compose.runOnUiThread { restoreKey.value = TvSearchEntryKey }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Search").assertIsFocused()
        compose.onNode(hasText("Film", substring = true) and isFocused()).assertDoesNotExist()
    }

    /**
     * `TvLibraryChrome`'s own gap: arrival focus (here, `TvHome`'s cover)
     * lands from a `LaunchedEffect`, which runs at least one frame after
     * the chrome's own `BackHandler`s register during `setContent`'s
     * first composition — checked here, before this test's own
     * `waitForIdle()` lets that frame turn over. A Back landing in that
     * gap must not find every region's `hasFocus` still at its initial
     * `false`, indistinguishable from genuinely resting on the rail
     * (`TvLibraryTest.backAtTheCatalogRootGoesUpThroughThePillThenTheRailBeforeTheAppFinishes`'s
     * own, deliberate, case for that shape) and fall through to close the app.
     */
    @Test
    fun aBackBeforeArrivalFocusHasLandedAnywhereIsStillCaught() {
        lateinit var built: ActivityController<ComponentActivity>
        compose.runOnUiThread {
            built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvCatalogScreen(
                        state = ready(films(1)),
                        profile = TvChosenProfile(name = "Ada", onChoose = {}),
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onOpenList = {},
                        onCreateList = {},
                    )
                }
            }
            assertTrue(
                built.get().onBackPressedDispatcher.hasEnabledCallbacks(),
                "no region has taken arrival focus yet, so a Back landing right now must still be caught rather than falling through",
            )
        }
        compose.waitForIdle()
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
