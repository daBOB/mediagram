package ui

import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import data.LiveSession
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import model.together.Member
import model.together.Session
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Join on the phone through the real library: Home reads the sessions as it
 * appears, offers the live one, and its Join opens that title in the player,
 * which is asked to join; once nothing shows Join the sessions are no longer
 * listened for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class TogetherJoinFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    private val live =
        LiveSession(41, Session("s", "film-1", true, 0.0, 1.0, 0, "tv", listOf(Member("tv", "TV"), Member("tab", "Tablet")), false))

    private fun open(available: Boolean = true) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            fixture.together.live.value = live
            fixture.together.available.value = available
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun homeReadsTheSessionsAndJoinOpensTheTitleToJoinIt() {
        open()
        assertTrue(fixture.together.refreshCount >= 1, "Home reads the sessions as it appears")
        compose.onNodeWithText("Watching together: Example Film · TV, Tablet").assertExists()

        compose.onNodeWithText("Join").performClick()

        compose.onNodeWithText("←").assertExists()
        verify { fixture.playback.media.setMediaItem(match { it.localConfiguration?.uri?.lastPathSegment == "film-1" }, any<Long>()) }
        assertEquals(41, fixture.together.takeJoin())
    }

    @Test
    fun theSessionsTitlePageJoinsInPlaceOfPlay() {
        open()
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        compose.onAllNodes(hasText("Example Film") and hasClickAction()).onFirst().performClick()

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("▶ Join").performClick()

        compose.onNodeWithText("←").assertExists()
        assertEquals(41, fixture.together.takeJoin())
    }

    @Test
    fun theSessionsShowPageJoinsInPlaceOfItsResumePill() {
        open()
        compose.runOnUiThread { fixture.together.live.value = live.copy(session = live.session.copy(set = "episode-1")) }
        compose.onNode(hasText("Series") and hasClickAction()).performClick()
        compose.onNode(hasText("Example Show") and hasClickAction()).performClick()

        compose.onNodeWithText("▶ Join").performClick()

        compose.onNodeWithText("←").assertExists()
        assertEquals(41, fixture.together.takeJoin())
    }

    /** A department tab draws no Join, so nothing listens for sessions while one is shown. */
    @Test
    fun onADepartmentTabTheSessionsAreNotListenedFor() {
        open()
        assertTrue(fixture.together.live.subscriptionCount.value > 0, "Home listens while it shows Join")

        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        compose.onNodeWithText("Watching together", substring = true).assertDoesNotExist()
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)) }
        compose.waitForIdle()

        assertEquals(0, fixture.together.live.subscriptionCount.value)
    }

    @Test
    fun outOfTelegramsReachHomeOffersNothing() {
        open(available = false)
        compose.onNodeWithText("mediagram").assertExists()
        compose.onNodeWithText("Watching together", substring = true).assertDoesNotExist()
    }

    @Test
    fun withHomeOutOfSightTheSessionsAreNoLongerListenedFor() {
        open()
        assertTrue(fixture.together.live.subscriptionCount.value > 0, "Home listens while it shows Join")

        compose.onNodeWithText("Join").performClick()
        compose.onNodeWithText("←").assertExists()
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6)) }
        compose.waitForIdle()

        assertEquals(0, fixture.together.live.subscriptionCount.value)
    }
}
