package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import catalog.CategoryRow
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.ShowsDepartment
import catalog.Underway
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
import kotlin.test.assertTrue

/**
 * A Shows department's Continue row card must play, like every card on the
 * shelves does (`home-resume.js`), rather than open a title page. The
 * Series-only Popular/New rows are the model's to decide, and are tested
 * there (`DepartmentsTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class ShowsDepartmentScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun ep(id: String): MediaSet =
        MediaSet(
            setId = id, kind = Kind.EPISODE, title = "Ep", show = "Show $id",
            chapter = null, path = null, season = 1, episodeFirst = 1, episodeLast = null,
            year = 2020, durationSecs = 1800, posterPath = null, totalBytes = 10,
        )

    private fun course(name: String, category: String?): Entry.Collection {
        val lesson = MediaSet(
            setId = "$name-lesson", kind = Kind.TUTORIAL, title = "Lesson", show = name, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, category = category,
        )
        return Entry.Collection(
            key = "COURSE/$name", kind = CollectionKind.COURSE, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, null, listOf(lesson), emptyList())),
        )
    }

    private fun department(underway: Underway, categories: List<CategoryRow<Entry.Collection>> = emptyList()) =
        ShowsDepartment(
            showCount = 1, itemCount = 1, lead = null, underway = underway, categories = categories,
            popular = emptyList(), newEpisodes = emptyList(), all = emptyList(),
        )

    private fun render(
        label: String,
        department: ShowsDepartment,
        onPlay: (String) -> Unit = {},
        onOpenTitle: (String) -> Unit = {},
        onOpenCollection: (String) -> Unit = {},
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    ShowsDepartmentScreen(
                        label = label, unit = if (label == "Series") "episode" else "lesson",
                        department = department, watch = WatchSnapshot.Empty, heldIds = emptySet(), columns = 3,
                        onOpenTitle = onOpenTitle, onOpenCollection = onOpenCollection, onPlay = onPlay,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun aContinueRowCardPlaysRatherThanOpeningATitlePage() {
        var played: String? = null
        var opened: String? = null
        render(
            "Series",
            department(Underway(listOf(ep("s1")), emptyList(), 1, 0)),
            onPlay = { played = it },
            onOpenTitle = { opened = it },
        )
        compose.onNode(hasText("Show s1", substring = true) and hasClickAction()).performClick()
        assertTrue(played == "s1" && opened == null, "expected the Continue card to play, got played=$played opened=$opened")
    }

    @Test fun aCategoryRowDrawsItsHeadingAndOpensItsCourseOnTap() {
        var opened: String? = null
        render(
            "Tutorials",
            department(
                Underway(emptyList(), emptyList(), 0, 0),
                categories = listOf(CategoryRow("Trading", listOf(course("Forex", "Trading")))),
            ),
            onOpenCollection = { opened = it },
        )
        compose.onNode(hasText("Trading", substring = true)).assertExists()
        compose.onNode(hasText("Forex", substring = true) and hasClickAction()).performClick()
        assertTrue(opened == "COURSE/Forex", "expected the category row's course to open, got $opened")
    }

    @Test fun noCategoryHeadingDrawsWhenNothingIsFiled() {
        render("Tutorials", department(Underway(emptyList(), emptyList(), 0, 0)))
        compose.onAllNodes(hasText("Other")).assertCountEquals(0)
    }
}
