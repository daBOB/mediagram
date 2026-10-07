package ui.common.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import data.PortraitRequestLog
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
 * [rememberTitleInfo] and its siblings below share the same shape: an
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
        assertEquals(failure, ShadowLog.getLogsForTag(FallbackTag).single().throwable)
        assertTrue(ShadowLog.getLogsForTag(FallbackTag).single().msg.contains("title details"))
    }

    /** A failed person lookup is an answer: the page stops saying "loading" and shows its empty sentence. */
    @Test
    fun aFailedPersonLookupFinishesLoadingWithNobody() {
        var lookup: PersonLookup? = null
        show { lookup = rememberPersonLookup(PersonId) { throw IllegalStateException("unreadable person row") } }
        assertEquals(PersonLookup(person = null, loading = false), lookup)
        assertTrue(ShadowLog.getLogsForTag(FallbackTag).single().msg.contains("person lookup"))
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
        assertTrue(ShadowLog.getLogsForTag(FallbackTag).isEmpty())
    }

    /**
     * A fetch cut short (here, by cancelling it outright, the same shape as
     * the composable being disposed mid-fetch) never finishes, so the next
     * card to ask for that person fetches again.
     */
    @Test
    fun aPortraitFetchCutShortIsRetriedLaterInTheSession() {
        var attempts = 0
        var result: String? = null
        val portraits = PortraitRequestLog()
        show {
            result =
                rememberPortrait(personId = PersonId, known = null, portraits = portraits) {
                    attempts++
                    throw CancellationException("left mid-fetch")
                }
        }
        assertEquals(1, attempts)
        assertEquals(null, result)

        show {
            result =
                rememberPortrait(personId = PersonId, known = null, portraits = portraits) {
                    attempts++
                    "portrait.jpg"
                }
        }
        assertEquals(2, attempts)
        assertEquals("portrait.jpg", result)
    }

    /** A fetch that ran to completion, successfully or not, is never retried. */
    @Test
    fun aFinishedPortraitFetchIsNotRetried() {
        var attempts = 0
        val portraits = PortraitRequestLog()
        show { rememberPortrait(personId = PersonId, known = null, portraits = portraits) { attempts++; "portrait.jpg" } }
        assertEquals(1, attempts)

        show { rememberPortrait(personId = PersonId, known = null, portraits = portraits) { attempts++; "portrait.jpg" } }
        assertEquals(1, attempts)
    }

    /**
     * A card leaving composition and coming back — a `LazyRow` scroll-out,
     * a tab switch — starts a fresh `remember` with [known] still `null`,
     * the same shape [aFinishedPortraitFetchIsNotRetried] exercises but
     * checking its returned portrait too, not just that no second fetch ran:
     * the found path must survive that remount, not fall back to initials
     * because the log already says no fetch is needed.
     */
    @Test
    fun aRemountedCardKeepsThePortraitAFinishedFetchFound() {
        var attempts = 0
        var result: String? = null
        val portraits = PortraitRequestLog()
        show {
            result =
                rememberPortrait(personId = PersonId, known = null, portraits = portraits) { attempts++; "portrait.jpg" }
        }
        assertEquals(1, attempts)
        assertEquals("portrait.jpg", result)

        result = null
        show {
            result =
                rememberPortrait(personId = PersonId, known = null, portraits = portraits) { attempts++; "portrait.jpg" }
        }
        assertEquals(1, attempts)
        assertEquals("portrait.jpg", result)
    }
}

/** The tag `data.orDefault` logs a failed lookup under. */
private const val FallbackTag = "fallback"

private const val PersonId = 90210001L
