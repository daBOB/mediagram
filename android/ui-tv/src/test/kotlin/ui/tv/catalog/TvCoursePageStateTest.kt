package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeriesResumePick
import model.Credit
import model.Kind
import model.TitleCredits
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A course's own television page as the web's `course-view.js` draws it:
 * the page's shelf head — the course's name, how many lessons and documents
 * it holds — over its lessons, with no tabs, art or facts above them; and
 * the one thing television keeps beyond the web's page, the resume line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCoursePageStateTest : TvScreenStateTest() {
    private val welcome = lesson("l1", "Welcome")
    private val more = lesson("l2", "More")
    private val course =
        Entry.Collection(
            key = "COURSE/A Course",
            kind = CollectionKind.COURSE,
            name = "A Course",
            posterPath = null,
            posterKey = null,
            count = 3,
            chapters = 2,
            divisions =
                listOf(
                    Division("Basics", null, listOf(welcome, lesson("d1", "Workbook").copy(kind = Kind.DOCUMENT)), emptyList()),
                    Division("Deeper", null, listOf(more), emptyList()),
                ),
        )

    @Test
    fun opensWithTheWebsShelfHeadOverItsLessons() {
        show { TvCollection(collection = course, info = null, watch = WatchSnapshot.Empty, onPlay = {}) }

        compose.onNode(hasText("A Course") and isHeading()).assertExists()
        compose.onNodeWithText("TWO LESSONS · ONE DOCUMENT").assertExists()
        val head = compose.onNodeWithText("TWO LESSONS · ONE DOCUMENT").getUnclippedBoundsInRoot()
        val folder = compose.onNodeWithText("Basics").getUnclippedBoundsInRoot()
        assertTrue(folder.top >= head.bottom, "the lessons should start under the head")
        compose.onNodeWithText("1. Welcome").assertIsFocused()
    }

    /** No tab row: a course has no About, Cast or Similar of its own to put in one, even when it is handed some. */
    @Test
    fun hasNoTabsEvenWhenHandedCreditsAndSimilar() {
        val credits = TitleCredits(cast = listOf(Credit(personId = 1L, name = "Ada Actor", role = null, portraitPath = null)), crew = emptyList())
        show { TvCollection(collection = course, info = null, watch = WatchSnapshot.Empty, onPlay = {}, credits = credits, similar = listOf(course.copy(key = "COURSE/Other", name = "Other"))) }

        listOf("Episodes", "About", "Cast", "Similar", "Ada Actor").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun comingBackFromALessonLandsOnIt() {
        show { TvCollection(collection = course, info = null, watch = WatchSnapshot.Empty, onPlay = {}, restoreKey = "l2") }

        compose.onNodeWithText("1. More").assertIsFocused()
    }

    /** The resume line sits under the head and plays its own lesson; the remote reaches it with Up from the first lesson, and Down goes back. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theResumeLineIsOnePressUpFromTheFirstLesson() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        var resumed: String? = null
        show {
            TvCollection(
                collection = course,
                info = null,
                watch = WatchSnapshot.Empty,
                onPlay = {},
                resume = SeriesResumePick(more, at = null, verb = ResumeVerb.CONTINUE),
                onResume = { resumed = it },
            )
        }
        compose.onNodeWithText("1. Welcome").assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithText("▶ Continue More").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("1. Welcome").assertIsFocused()

        compose.onNodeWithText("▶ Continue More").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("l2", resumed)
    }

    /**
     * A numbered lesson is named as a lesson, not by the chapter its index
     * files it under: "▶ Continue lesson 3", never "▶ Continue S1 E3".
     */
    @Test
    fun theResumeLineNamesALessonByItsNumber() {
        val third = lesson("l3", "Signals").copy(season = 1, episodeFirst = 3, episodeLast = 3)
        show {
            TvCollection(
                collection = course,
                info = null,
                watch = WatchSnapshot.Empty,
                onPlay = {},
                resume = SeriesResumePick(third, at = null, verb = ResumeVerb.CONTINUE),
            )
        }

        compose.onNodeWithText("▶ Continue lesson 3").assertExists()
        compose.onNodeWithText("S1 E3", substring = true).assertDoesNotExist()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun lesson(
        id: String,
        title: String,
    ) = set(id, Kind.TUTORIAL, title, show = "A Course", addedAt = 0)
}
