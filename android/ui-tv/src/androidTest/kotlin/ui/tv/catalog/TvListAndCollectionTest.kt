package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.ListOfSets
import model.MediaSet
import model.WatchSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/**
 * The D-pad paths through a list's page and a course's rows, which only
 * run true on a real window manager — what composes where is proven
 * without one in `TvListStateTest` and `TvCollectionStateTest`.
 */
@RunWith(AndroidJUnit4::class)
class TvListAndCollectionTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun downFromATitleOnAListReachesItsRemove() {
        show { TvList(ListOfSets("a", "Sunday", emptyList()), sets(3), {}, {}, {}, {}) }
        waitUntilFocused("Title 0")

        press(Key.DirectionDown)

        waitUntilFocused("Remove")
    }

    @Test
    fun upFromTheFirstTitlesReachesTheListsActions() {
        show { TvList(ListOfSets("a", "Sunday", emptyList()), sets(3), {}, {}, {}, {}) }
        waitUntilFocused("Title 0")

        press(Key.DirectionUp)

        waitUntilFocused("Rename")
    }

    @Test
    fun downThroughACoursePassesOverFolderHeadings() {
        val course =
            Entry.Collection(
                key = "COURSE/A Course",
                kind = CollectionKind.COURSE,
                name = "A Course",
                posterPath = null,
                posterKey = null,
                count = 2,
                chapters = 2,
                divisions =
                    listOf(
                        Division("Basics", null, listOf(set("l1", "Welcome")), emptyList()),
                        Division("Deeper", null, listOf(set("l2", "More")), emptyList()),
                    ),
            )
        show { TvCollection(course, null, WatchSnapshot.Empty, { null }, {}, {}) }
        waitUntilFocused("1. Welcome")

        press(Key.DirectionDown)

        waitUntilFocused("1. More")
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TvTheme { content() } }
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    }

    private fun waitUntilFocused(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sets(count: Int) = (0 until count).map { set("set-$it", "Title $it") }

    private fun set(
        id: String,
        title: String,
    ) = MediaSet(
        setId = id,
        kind = Kind.TUTORIAL,
        title = title,
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
        addedAt = 0,
    )
}
