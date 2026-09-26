package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertTrue

/**
 * [rememberTitleInfo]/[rememberTitleCredits]'s own exception handling —
 * swallowing an ordinary failure, letting a cancellation through — is
 * [ui.catalog.RememberLookupTest]'s, in ui-common. What belongs here is
 * whether the screens built on top of them stay usable regardless: a Play
 * button that still plays, and stays on screen, through a lookup that never
 * resolves — whichever of a title's several lookups it was.
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
                onOpenGenre = {},
            )
        }
        compose.onNodeWithText("▶ Play").performClick()
        assertTrue(played)
    }

    @Test fun titleLookupCancellationLeavesThePlayButtonDisplayed() {
        show {
            TitleDetailScreen(
                film,
                rememberTitleInfo("tmdb-movie-1") { throw CancellationException("left title") },
                {},
                onOpenGenre = {},
            )
        }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    @Test fun unreadableCreditsLeaveTheTitlePlayable() {
        var played = false
        show {
            TitleDetailScreen(
                film,
                info = null,
                onPlay = { played = true },
                onOpenGenre = {},
                titleCredits = { throw IllegalStateException("unreadable credits") },
            )
        }
        compose.onNodeWithText("▶ Play").performClick()
        assertTrue(played)
    }

    @Test fun creditsLookupCancellationLeavesThePlayButtonDisplayed() {
        show {
            TitleDetailScreen(
                film,
                info = null,
                onPlay = {},
                onOpenGenre = {},
                titleCredits = { throw CancellationException("left title") },
            )
        }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    private val film = MediaSet("film", Kind.MOVIE, "Held Film", null, null, null, null, null, null, 2020, 120, null, 10)
}
