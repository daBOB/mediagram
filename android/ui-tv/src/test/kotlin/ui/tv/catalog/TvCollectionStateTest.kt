package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeriesResumePick
import catalog.seasonOptionOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import model.Watched
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TvCollection]: a show's Episodes tab is the web's season
 * picker over one season's episodes, a course the phone's indented rows,
 * and a document is a line that says why it does not open rather than a
 * thing to press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCollectionStateTest : TvScreenStateTest() {
    @Test
    fun aShowOfSeveralSeasonsPicksASeasonAndListsItsEpisodes() {
        val show = show(division("Season 1", 1, episode("e1", "Pilot")), division("Season 2", 2, episode("e2", "Return"), episode("e3", "Again")))
        val season = mutableStateOf<String?>(null)
        show { TvCollection(show, info = null, watch = WatchSnapshot.Empty, onPlay = {}, season = season.value, onSelectSeason = { season.value = it }) }

        compose.onNodeWithText("A Show").assertExists()
        compose.onNodeWithTag(TvSeasonPickerTag).assertExists()
        compose.onNodeWithText("1. Pilot").assertExists()
        compose.onNodeWithText("1. Return").assertDoesNotExist()

        // The web's own option words, counted as `countOf` counts.
        compose.onNodeWithText("Season 2 · two episodes").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Season 2 · two episodes").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Season 2", season.value)
        compose.onNodeWithText("1. Return").assertExists()
        compose.onNodeWithText("1. Pilot").assertDoesNotExist()
        // The pill just pressed keeps the remote while the list under it changes.
        compose.onNodeWithText("Season 2 · two episodes").assertIsFocused()
        // The picker names the season; the list under it does not name it again.
        compose.onAllNodesWithText("Season 2").assertCountEquals(0)
    }

    /** One season has nothing to pick from, as `series-page.js` leaves its select out. */
    @Test
    fun aShowOfOneSeasonListsItsEpisodesWithNoPicker() {
        var played: String? = null
        val show = show(division("Season 1", 1, episode("e1", "Pilot"), episode("e2", "Return")))
        showCollection(show, onPlay = { played = it }, watch = WatchSnapshot.Empty.copy(watched = listOf(Watched("e1", 1))))

        compose.onNodeWithTag(TvSeasonPickerTag).assertDoesNotExist()
        compose.onNodeWithText("1. ✓ Pilot").assertExists()
        compose.onNodeWithText("2. Return").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("e2", played)
    }

    /** Down from the pills enters Episodes, then the picker, then that season's first episode — and Up walks back to the tab. */
    @Test
    fun theRemoteWalksDownFromThePillsThroughThePickerIntoTheEpisodes() {
        val show = show(division("Season 1", 1, episode("e1", "Pilot")), division("Season 2", 2, episode("e2", "Return")))
        showCollection(show)
        compose.onNodeWithText("+ My List").assertIsFocused()

        compose.onNodeWithText("+ My List").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Episodes").assertIsFocused()
        compose.onNodeWithText("Episodes").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Season 1 · one episode").assertIsFocused()
        compose.onNodeWithText("Season 1 · one episode").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("1. Pilot").assertIsFocused()

        compose.onNodeWithText("1. Pilot").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Season 1 · one episode").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Episodes").assertIsFocused()
    }

    @Test
    fun comingBackLandsOnTheEpisodeThatWasOpened() {
        val show = show(division("Season 1", 1, episode("e1", "Pilot"), episode("e2", "Return")))
        showCollection(show, restoreKey = "e2")

        compose.onNodeWithText("2. Return").assertIsFocused()
    }

    /** Back from an episode of a season nobody picked still finds it: the page shows the season that holds it. */
    @Test
    fun comingBackToAnotherSeasonsEpisodeShowsThatSeason() {
        val show = show(division("Season 1", 1, episode("e1", "Pilot")), division("Season 2", 2, episode("e2", "Return")))
        showCollection(show, restoreKey = "e2")

        compose.onNodeWithText("1. Return").assertIsFocused()
    }

    @Test
    fun aCourseHeadsEachFolderAndADocumentSaysWhyItDoesNotOpen() {
        val handout = document("d1", "Workbook")
        val course =
            collection(
                CollectionKind.COURSE,
                "A Course",
                Division("Basics", null, listOf(handout, lesson("l1", "Welcome")), listOf(division("Deeper", null, lesson("l2", "More")))),
            )
        showCollection(course)

        compose.onNodeWithText("Basics").assertExists()
        compose.onNodeWithText("Deeper").assertExists()
        compose.onNodeWithText("Document — the television cannot open one yet").assertExists()
        val document = compose.onNodeWithText("1. Workbook", substring = true).fetchSemanticsNode()
        assertFalse(SemanticsActions.OnClick in document.config, "a document offers nothing to press")
        assertFalse(document.config.getOrElse(SemanticsProperties.Focused) { false })
        // The remote passes over the document to the first thing that opens.
        compose.onNodeWithText("2. Welcome").assertIsFocused()
    }

    @Test
    fun aCourseOfDocumentsOnlyFocusesItsFirstDocumentWithoutOfferingAPress() {
        val course = collection(CollectionKind.COURSE, "Handouts", division("Basics", null, document("d1", "Workbook"), document("d2", "Answers")))
        showCollection(course)

        compose.onNodeWithText("1. Workbook", substring = true).assertIsFocused()
        val node = compose.onNodeWithText("1. Workbook", substring = true).fetchSemanticsNode()
        assertFalse(SemanticsActions.OnClick in node.config, "a document offers nothing to press")
        assertTrue(SemanticsProperties.Disabled in node.config, "a document reads as disabled")
    }

    @Test
    fun anEpisodeThisDeviceHoldsSaysOffline() {
        showCollection(show(division("Season 2", 2, episode("e3", "Late"), episode("e4", "Later"))), heldIds = setOf("e4"))

        compose.onAllNodesWithTag(TvOfflineBadgeTag).assertCountEquals(1)
        compose.onNodeWithText("offline").assertExists()
    }

    /**
     * A show resuming in Season 5 of 6 opens with Season 5's pill in sight —
     * the picker is the only place the shown season is named — and Down from
     * the Episodes tab lands on that pill rather than on Season 1.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theShownSeasonsPillIsInSightAndTakesTheRemoteFromTheTab() {
        val seasons = (1..6).map { n -> division("Season $n", n, episode("s${n}e1", "Opener $n")) }
        val fifth = seasons[4].items.single()
        val fifthPill = seasonOptionOf(seasons[4])
        showCollection(show(*seasons.toTypedArray()), resume = SeriesResumePick(fifth, at = null, verb = ResumeVerb.CONTINUE))
        compose.onNodeWithText("1. Opener 5").assertExists()

        // Real type, so six pills run past the screen's edge as they do on the box.
        val sixth = compose.onNodeWithText(seasonOptionOf(seasons[5])).getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(sixth.right > screen.right, "expected the row to run past the screen, got Season 6 at $sixth")
        val pill = compose.onNodeWithText(fifthPill).getUnclippedBoundsInRoot()
        assertTrue(pill.left >= screen.left && pill.right <= screen.right, "expected Season 5's pill on screen, got $pill on $screen")

        compose.onNodeWithText("Episodes").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Episodes").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(fifthPill).assertIsFocused()
    }

    private fun showCollection(
        collection: Entry.Collection,
        watch: WatchSnapshot = WatchSnapshot.Empty,
        onPlay: (String) -> Unit = {},
        restoreKey: String? = null,
        heldIds: Set<String> = emptySet(),
        resume: SeriesResumePick? = null,
    ) = show {
        TvCollection(
            collection = collection,
            info = null,
            watch = watch,
            onPlay = onPlay,
            restoreKey = restoreKey,
            heldIds = heldIds,
            resume = resume,
        )
    }

    private fun show(vararg divisions: Division) = collection(CollectionKind.SHOW, "A Show", *divisions)

    private fun collection(
        kind: CollectionKind,
        name: String,
        vararg divisions: Division,
    ) = Entry.Collection(
        key = "$kind/$name",
        kind = kind,
        name = name,
        posterPath = null,
        posterKey = null,
        count = divisions.sumOf { it.items.size },
        chapters = divisions.size,
        divisions = divisions.toList(),
    )

    private fun division(
        title: String,
        season: Int?,
        vararg items: MediaSet,
    ) = Division(title, season, items.toList(), emptyList())

    private fun episode(
        id: String,
        title: String,
    ) = set(id, Kind.EPISODE, title, show = "A Show", addedAt = 0)

    private fun document(
        id: String,
        title: String,
    ) = lesson(id, title).copy(kind = Kind.DOCUMENT)

    private fun lesson(
        id: String,
        title: String,
    ) = set(id, Kind.TUTORIAL, title, show = "A Course", addedAt = 0)
}
