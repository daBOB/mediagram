package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import catalog.CategoryRow
import catalog.CollectionKind
import catalog.DocumentaryGroupRow
import catalog.Entry
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
import kotlin.test.assertEquals

/**
 * The Documentaries department's opening page — a Compose port of
 * `renderDocumentariesDept`. Checked at the phone's default width and at the
 * tablet's own `w1164dp-h777dp`, the same window `HomeScreenTest` and
 * `LibraryRailTest` check the rest of this chrome at; a row further down the
 * page than either window's own height is scrolled to first
 * ([performScrollToNode]) rather than asserted on sight, since a `LazyColumn`
 * composes what a real device would — the visible rows, not the whole list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentariesDepartmentScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun docSet(
        id: String,
        backdrop: String? = null,
        tagline: String? = null,
    ) = MediaSet(
        setId = id, kind = Kind.DOCUMENTARY, title = id, show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2019, durationSecs = 3_000,
        posterPath = "p-$id.jpg", totalBytes = 0, backdropPath = backdrop, tagline = tagline,
    )

    private val group = DocumentaryGroupRow(
        collection = Entry.Collection(
            key = "DOCUMENTARY/Terra X", kind = CollectionKind.COURSE, name = "Terra X", posterPath = null, posterKey = null,
            count = 15, chapters = 3, divisions = emptyList(),
        ),
        preview = listOf(docSet("ep-1"), docSet("ep-2")),
    )

    // Fully qualified: catalog.DocumentariesDepartment is the pure data
    // class this fixture builds; the unqualified name in this file's own
    // package is DocumentariesDepartmentScreen.kt's composable wrapper.
    private val department = catalog.DocumentariesDepartment(
        itemCount = 24,
        lead = docSet("lead", backdrop = "lead-bg", tagline = "A quotable line."),
        continuing = listOf(docSet("resuming")),
        recentlyAdded = listOf(docSet("recent")),
        categories = emptyList(),
        collections = listOf(group),
        singles = listOf(docSet("standalone")),
    )

    private var played: String? = null
    private var openedCollection: String? = null

    private fun show() {
        played = null
        openedCollection = null
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    DocumentariesDepartmentScreen(
                        department = department,
                        watch = WatchSnapshot.Empty,
                        heldIds = emptySet(),
                        onOpenCollection = { openedCollection = it },
                        onPlay = { played = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Scrolls the page's own [LazyColumn][androidx.compose.foundation.lazy.LazyColumn] until a row matching [text] is composed, then asserts it drew. */
    private fun assertRowReachable(text: String) {
        val matcher: SemanticsMatcher = hasText(text)
        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).assertIsDisplayed()
    }

    @Test
    fun everySectionAppearsAtThePhonesDefaultWidth() {
        show()
        assertRowReachable("DOCUMENTARIES")
        assertRowReachable("24 documentaries")
        assertRowReachable("Continue watching")
        assertRowReachable("Recently added")
        assertRowReachable("Terra X")
        assertRowReachable("Standalone documentaries")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun everySectionAppearsAtTheTabletsOwnWidth() {
        show()
        assertRowReachable("DOCUMENTARIES")
        assertRowReachable("Continue watching")
        assertRowReachable("Recently added")
        assertRowReachable("Terra X")
        assertRowReachable("Standalone documentaries")
    }

    /** The web wires every plate here to play, not to a title page — no synopsis or cast to stop for. */
    @Test
    fun tappingAStandaloneDocumentaryPlaysItRatherThanOpeningATitlePage() {
        show()
        val matcher = hasText("standalone")
        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
        assertEquals("standalone", played)
        assertEquals(null, openedCollection)
    }

    @Test
    fun theFoldersAllLinkOnItsHeadingOpensTheCollectionRatherThanPlaying() {
        show()
        val matcher = hasText("All 15 →")
        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
        assertEquals("DOCUMENTARY/Terra X", openedCollection)
        assertEquals(null, played)
    }

    /** A category mixes a folder and a single side by side: the folder opens, the single plays. */
    @Test
    fun aCategoryRowOpensItsFolderAndPlaysItsSingle() {
        val folder = Entry.Collection(
            key = "DOCUMENTARY/Cosmos", kind = CollectionKind.COURSE, name = "Cosmos", posterPath = null, posterKey = null,
            count = 5, chapters = 1, divisions = emptyList(),
        )
        val categorised = department.copy(categories = listOf(CategoryRow("Science", listOf(folder, Entry.Film(docSet("solo"))))))
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    DocumentariesDepartmentScreen(
                        department = categorised,
                        watch = WatchSnapshot.Empty,
                        heldIds = emptySet(),
                        onOpenCollection = { openedCollection = it },
                        onPlay = { played = it },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(hasText("Cosmos"))
        compose.onNode(hasText("Cosmos", substring = true) and hasClickAction()).performClick()
        assertEquals("DOCUMENTARY/Cosmos", openedCollection)

        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(hasText("solo"))
        compose.onNode(hasText("solo", substring = true) and hasClickAction()).performClick()
        assertEquals("solo", played)
    }

    /** The web's `leadHref: null` — the hero is not a link, unlike Movies' own hero. */
    @Test
    fun tappingTheHeroDoesNothing() {
        show()
        compose.onNodeWithTag(DOCUMENTARIES_DEPT_TEST_TAG).performScrollToNode(hasText("DOCUMENTARIES"))
        compose.onNode(hasText("DOCUMENTARIES")).performClick()
        assertEquals(null, played)
        assertEquals(null, openedCollection)
    }

    @Test
    fun anEmptyDepartmentShowsTheUploadHintRatherThanABlankPage() {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    DocumentariesDepartment(
                        shelf = catalog.Shelf(catalog.DOCUMENTARIES, emptyList()),
                        state = catalog.CatalogUiState.Ready(emptyList()),
                        onOpenCollection = {},
                        onPlay = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("No documentaries yet. Upload one with mediagram add-docu <file|folder>.", substring = true).assertIsDisplayed()
    }
}
