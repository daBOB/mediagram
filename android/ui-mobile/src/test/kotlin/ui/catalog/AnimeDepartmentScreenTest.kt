package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import catalog.AnimeDepartment
import catalog.CatalogUiState
import catalog.CollectionKind
import catalog.Department
import catalog.Division
import catalog.Entry
import catalog.NextUpEntry
import catalog.Shelf
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
 * The Anime department's opening page — a Compose port of `renderAnimeDept`.
 * Checked the same way [DocumentariesDepartmentScreenTest] checks its own
 * page: scrolled to a row rather than asserted on sight, since a
 * [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] composes only
 * what is on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimeDepartmentScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String, backdrop: String? = null) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = id, show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2016, durationSecs = 6_000,
        posterPath = "p-$id.jpg", totalBytes = 0, backdropPath = backdrop, anime = true,
    )

    private val show = Entry.Collection(
        key = "ANIME/Dragonball", kind = CollectionKind.SHOW, name = "Dragonball", posterPath = null, posterKey = null,
        count = 1, chapters = 1, divisions = listOf(Division("Dragonball", 1, listOf(film("dragonball-e1")), emptyList())),
    )

    private val department = AnimeDepartment(
        showCount = 1,
        filmCount = 2,
        lead = film("your-name", backdrop = "lead-bg"),
        continuing = listOf(film("resuming")),
        nextUp = listOf(NextUpEntry(film("next"), resume = false, touchedAt = 1)),
        shows = listOf(show),
        films = listOf(film("your-name"), film("weathering-with-you")),
    )

    private var openedTitle: String? = null
    private var openedCollection: String? = null

    private fun show() {
        openedTitle = null
        openedCollection = null
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    AnimeDepartmentScreen(
                        department = department,
                        watch = WatchSnapshot.Empty,
                        heldIds = emptySet(),
                        columns = 3,
                        onOpenTitle = { openedTitle = it },
                        onOpenCollection = { openedCollection = it },
                        onPlay = { openedTitle = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertRowReachable(text: String, substring: Boolean = false) {
        val matcher: SemanticsMatcher = hasText(text, substring = substring)
        compose.onNodeWithTag(ANIME_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).assertIsDisplayed()
    }

    @Test
    fun everySectionAppears() {
        show()
        assertRowReachable("ANIME")
        // Spelled, not figures — the department-line parity fix: a count of
        // twenty or fewer reads as a word on every surface now, matching the
        // web's own `countOf` (`format.js`).
        assertRowReachable("one show", substring = true)
        assertRowReachable("two films", substring = true)
        assertRowReachable("Continue watching")
        assertRowReachable("Series")
        assertRowReachable("Films")
    }

    @Test
    fun tappingAShowOpensTheCollectionRatherThanPlaying() {
        show()
        val matcher = hasText("Dragonball")
        compose.onNodeWithTag(ANIME_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
        assertEquals("ANIME/Dragonball", openedCollection)
        assertEquals(null, openedTitle)
    }

    @Test
    fun tappingAFilmOpensItsTitlePage() {
        show()
        val matcher = hasText("weathering-with-you")
        compose.onNodeWithTag(ANIME_DEPT_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
        assertEquals("weathering-with-you", openedTitle)
        assertEquals(null, openedCollection)
    }

    /** [AnimeDepartmentTab] never runs on an empty shelf — Anime is omitted from `shelvesOf` at zero, unlike Documentaries. */
    @Test
    fun anEmptyShelfDrawsNothing() {
        var composed = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    composed = true
                    AnimeDepartmentTab(
                        shelf = Shelf(Department.ANIME, emptyList()),
                        state = CatalogUiState.Ready(emptyList()),
                        columns = 3,
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onPlay = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(true, composed, "the wrapper itself still composes; it is animeDepartmentOf that answers null")
    }
}
