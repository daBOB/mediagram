package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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

/**
 * [rememberTitleInfo] and [rememberPosterPath] share the same shape: an
 * ordinary failure is swallowed into a logged warning and a null result, but
 * a cancellation is let through as one. Exercised directly, without the
 * screens that call them — neither behaviour has anything to do with how
 * either one renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RememberLookupTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { content() }
        }
        compose.waitForIdle()
    }

    @Test
    fun unreadableTitleInfoReportsTheFailedLookup() {
        val failure = IllegalStateException("unreadable title row")
        show { rememberTitleInfo("tmdb-movie-1") { throw failure } }
        assertEquals(failure, ShadowLog.getLogsForTag("CatalogMetadata").single().throwable)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").single().msg.contains("title details"))
    }

    @Test
    fun unreadablePosterPathReportsTheFailedLookup() {
        val failure = IllegalStateException("unreadable poster path")
        show { rememberPosterPath("tmdb-tv-1-s1") { throw failure } }
        assertEquals(failure, ShadowLog.getLogsForTag("CatalogMetadata").single().throwable)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").single().msg.contains("season poster"))
    }

    @Test
    fun titleLookupCancellationRemainsCancellationWithoutAFailureDiagnostic() {
        var job: Job? = null
        show {
            rememberTitleInfo("tmdb-movie-1") {
                job = currentCoroutineContext()[Job]
                throw CancellationException("left title")
            }
        }
        assertTrue(requireNotNull(job).isCancelled)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").isEmpty())
    }

    @Test
    fun posterLookupCancellationRemainsCancellationWithoutAFailureDiagnostic() {
        var job: Job? = null
        show {
            rememberPosterPath("tmdb-tv-1-s1") {
                job = currentCoroutineContext()[Job]
                throw CancellationException("left season")
            }
        }
        assertTrue(requireNotNull(job).isCancelled)
        assertTrue(ShadowLog.getLogsForTag("CatalogMetadata").isEmpty())
    }
}
