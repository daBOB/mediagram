package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.SeasonPlate
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [rememberTitleInfo]/[rememberPosterPath]'s own exception handling —
 * swallowing an ordinary failure, letting a cancellation through — is
 * [ui.catalog.RememberLookupTest]'s, in ui-common. What belongs here is
 * whether the screens built on top of them stay usable regardless: a Play
 * button that still plays, a season plate that still opens, both still on
 * screen through a lookup that never resolves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OptionalMetadataTest {
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

    @Test fun unreadableTitleDetailsLeaveTheTitlePlayable() {
        var played = false
        show {
            TitleDetailScreen(
                film,
                rememberTitleInfo("tmdb-movie-1") { throw IllegalStateException("unreadable title row") },
                { played = true },
            )
        }
        compose.onNodeWithText("▶ Play").performClick()
        assertTrue(played)
    }

    @Test fun unreadableSeasonArtworkLeavesTheSeasonOpenable() {
        var opened: Division? = null
        show { SeasonFixture({ throw IllegalStateException("unreadable poster path") }) { opened = it } }
        compose.onNodeWithText("Season One").assertIsDisplayed().performClick()
        assertEquals(division, opened)
    }

    @Test fun titleLookupCancellationLeavesThePlayButtonDisplayed() {
        show {
            TitleDetailScreen(
                film,
                rememberTitleInfo("tmdb-movie-1") { throw CancellationException("left title") },
                {},
            )
        }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    @Test fun posterLookupCancellationLeavesTheSeasonPlateDisplayed() {
        show { SeasonFixture({ throw CancellationException("left season") }, {}) }
        compose.onNodeWithText("Season One").assertIsDisplayed()
    }

    @Composable private fun SeasonFixture(
        lookup: suspend (String) -> String?,
        open: (Division) -> Unit,
    ) {
        SeasonWall(
            Entry.Collection("show", CollectionKind.SHOW, "Show", null, "tmdb-tv-1", 1, 1, listOf(division)),
            null,
            listOf(SeasonPlate("Season One", "1 episode", "tmdb-tv-1-s1", division, false)),
            lookup,
            open,
        )
    }

    private val division = Division("Season One", 1, emptyList(), emptyList())
    private val film = MediaSet("film", Kind.MOVIE, "Held Film", null, null, null, null, null, null, 2020, 120, null, 10)
}
