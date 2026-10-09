package ui.tv

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import data.LiveSession
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.Profile
import model.together.Member
import model.together.Session
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Join on the television through the real library: Home reads the sessions
 * as it appears and shows the plate under the cover; Down from the cover
 * reaches it, and OK opens the session's title in the player, which is
 * asked to join it. The session is heard after Home's arrival has landed,
 * so the remote stays on the cover, as for any band filled in later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>
    private val plate = hasText("Watching together: Film 1 · TV, Tablet")
    private val live =
        LiveSession(41, Session("s", "film-1", true, 0.0, 1.0, 0, "tv", listOf(Member("tv", "TV"), Member("tab", "Tablet")), false))

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            val films =
                (0 until 3).map {
                    set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()).copy(backdropPath = "/b-$it.jpg", posterPath = "/p-$it.jpg")
                }
            val pilot = set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 20, episode = 1).copy(season = 1)
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films + pilot)
            fixture.together.live.value = live
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(plate).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    @Test
    fun theSessionsTitlePageJoinsInPlaceOfPlay() {
        val poster = hasContentDescription("Film 1") and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(poster)
        compose.onNode(poster).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)

        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
        assertEquals(41, fixture.together.takeJoin())
    }

    @Test
    fun theSessionsShowPageJoinsInPlaceOfItsResumePill() {
        compose.runOnUiThread { fixture.together.live.value = live.copy(session = live.session.copy(set = "pilot")) }
        val poster = hasText("A Show") and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(poster)
        compose.onNode(poster).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("▶ Join").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)

        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
        assertEquals(41, fixture.together.takeJoin())
    }

    /** A department tab draws no Join, so nothing listens for sessions while one is shown. */
    @Test
    fun onADepartmentTabTheSessionsAreNotListenedFor() {
        assertTrue(fixture.together.live.subscriptionCount.value > 0, "Home listens while it shows Join")

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(plate).assertDoesNotExist()
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)) }
        compose.waitForIdle()

        assertEquals(0, fixture.together.live.subscriptionCount.value)
    }

    @Test
    fun downFromTheCoverReachesThePlateAndOkOpensTheTitleToJoinIt() {
        assertTrue(fixture.together.refreshCount >= 1, "Home reads the sessions as it appears")
        compose.onNode(hasContentDescription("Watch now: ", substring = true)).assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(plate).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)

        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
        assertEquals(41, fixture.together.takeJoin())
    }
}
