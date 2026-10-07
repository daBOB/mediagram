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
     * [rememberPortrait] marks a person's reservation reserved-not-done the
     * moment it starts a fetch; a fetch cut short (here, by cancelling it
     * outright, the same shape as the composable being disposed mid-fetch)
     * is retried the next time something asks, even though [shouldRequest]
     * — standing in for `data.PortraitRequestLog`'s own shared "already
     * asked" answer — says no a second time.
     */
    @Test
    fun aPortraitFetchCutShortIsRetriedLaterInTheSession() {
        var attempts = 0
        var result: String? = null
        // A fake with the same shape as the real `PortraitRequestLog.shouldRequest`:
        // true only the first time this session, never again after that.
        val reserved = mutableSetOf<Long>()
        val shouldRequest: (Long) -> Boolean = { id -> reserved.add(id) }
        show {
            result =
                rememberPortrait(personId = PersonId, known = null, shouldRequest = shouldRequest) {
                    attempts++
                    throw CancellationException("left mid-fetch")
                }
        }
        assertEquals(1, attempts)
        assertEquals(null, result)

        // The shared log still says no — already reserved on the first ask
        // — proving the retry does not wait for it to say yes again.
        show {
            result =
                rememberPortrait(personId = PersonId, known = null, shouldRequest = shouldRequest) {
                    attempts++
                    "portrait.jpg"
                }
        }
        assertEquals(2, attempts)
        assertEquals("portrait.jpg", result)
    }

    /** A fetch that ran to completion, successfully or not, is never retried — [shouldRequest] alone still gates it. */
    @Test
    fun aFinishedPortraitFetchIsNotRetried() {
        var attempts = 0
        val reserved = mutableSetOf<Long>()
        val shouldRequest: (Long) -> Boolean = { id -> reserved.add(id) }
        show { rememberPortrait(personId = PersonId + 1, known = null, shouldRequest = shouldRequest) { attempts++; "portrait.jpg" } }
        assertEquals(1, attempts)

        show { rememberPortrait(personId = PersonId + 1, known = null, shouldRequest = shouldRequest) { attempts++; "portrait.jpg" } }
        assertEquals(1, attempts)
    }

    /**
     * A card leaving composition and coming back — a `LazyRow` scroll-out,
     * a tab switch — starts a fresh `remember` with [known] still `null`,
     * the same shape [aFinishedPortraitFetchIsNotRetried] exercises but
     * checking its returned portrait too, not just that no second fetch ran:
     * the found path must survive that remount, not fall back to initials
     * because the shared log already says no.
     */
    @Test
    fun aRemountedCardKeepsThePortraitAFinishedFetchFound() {
        var attempts = 0
        var result: String? = null
        val reserved = mutableSetOf<Long>()
        val shouldRequest: (Long) -> Boolean = { id -> reserved.add(id) }
        show {
            result =
                rememberPortrait(personId = PersonId + 2, known = null, shouldRequest = shouldRequest) { attempts++; "portrait.jpg" }
        }
        assertEquals(1, attempts)
        assertEquals("portrait.jpg", result)

        result = null
        show {
            result =
                rememberPortrait(personId = PersonId + 2, known = null, shouldRequest = shouldRequest) { attempts++; "portrait.jpg" }
        }
        assertEquals(1, attempts)
        assertEquals("portrait.jpg", result)
    }
}

/** The tag `data.orDefault` logs a failed lookup under. */
private const val FallbackTag = "fallback"

/** Distinct from any personId another test in this class or module might use, so the process-wide portrait sets never collide across tests. */
private const val PersonId = 90210001L
