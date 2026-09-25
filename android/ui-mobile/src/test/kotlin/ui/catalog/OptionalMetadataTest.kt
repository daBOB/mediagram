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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test fun unreadableTitleDetailsLeaveTheTitlePlayableAndReportTheFailedLookup() {
        val failure = IllegalStateException("unreadable title row")
        var played = false
        show {
            TitleDetailScreen(film, rememberTitleInfo("tmdb-movie-1") { throw failure }, { played = true }, onOpenGenre = {})
        }
        compose.onNodeWithText("▶ Play").performClick()
        assertTrue(played)
        assertEquals(failure, ShadowLog.getLogsForTag("CatalogMetadata").single().throwable)
        assertTrue(
            ShadowLog
                .getLogsForTag("CatalogMetadata")
                .single()
                .msg
                .contains("title details"),
        )
    }

    @Test fun unreadableSeasonArtworkLeavesTheSeasonOpenableAndReportsTheFailedLookup() {
        val failure = IllegalStateException("unreadable poster path")
        var opened: Division? = null
        show { SeasonFixture({ throw failure }) { opened = it } }
        compose.onNodeWithText("Season One").assertIsDisplayed().performClick()
        assertEquals(division, opened)
        assertEquals(failure, ShadowLog.getLogsForTag("CatalogMetadata").single().throwable)
        assertTrue(
            ShadowLog
                .getLogsForTag("CatalogMetadata")
                .single()
                .msg
                .contains("season poster"),
        )
    }

    @Test fun titleLookupCancellationRemainsCancellationWithoutAFailureDiagnostic() {
        var job: Job? = null
        show {
            TitleDetailScreen(
                film,
                rememberTitleInfo("tmdb-movie-1") {
                    job = currentCoroutineContext()[Job]
                    throw CancellationException("left title")
                },
                {},
                onOpenGenre = {},
            )
        }
        assertTrue(requireNotNull(job).isCancelled)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").isEmpty())
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    @Test fun posterLookupCancellationRemainsCancellationWithoutAFailureDiagnostic() {
        var job: Job? = null
        show {
            SeasonFixture({
                job = currentCoroutineContext()[Job]
                throw CancellationException("left season")
            }, {})
        }
        assertTrue(requireNotNull(job).isCancelled)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").isEmpty())
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
            onOpenGenre = {},
        )
    }

    private val division = Division("Season One", 1, emptyList(), emptyList())
    private val film = MediaSet("film", Kind.MOVIE, "Held Film", null, null, null, null, null, null, 2020, 120, null, 10)
}
