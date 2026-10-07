package ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.FilmPreloadState
import playback.LanSetStatus
import kotlin.test.assertEquals

/**
 * A film's own Preload control, over the real [ui.LibraryFlowFixture] and
 * [LibraryFlowTestActivity] — the same route [LibraryFlowTest] walks for
 * every other film-page control, previously untested here (the fixture
 * held no film at all, so the first real test to open one would have hit
 * the same `NoSuchMethodException` `TvAppFixture` needed fixing for).
 *
 * Two things only a real recomposition loop can catch: the server line's
 * own poll rate (a cold `Flow` rebuilt every recomposition restarts
 * its 5s wait on every progress tick instead of keeping it), and the
 * NeedsSpace → Settings › Storage → back-to-the-film route in full.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class FilmPreloadFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
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

    private fun openFilm() {
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        // The department front page can carry the one film under more than
        // one heading (Featured and Recently added both, with only one film
        // to choose between) — either leads to the same set id.
        compose.onAllNodes(hasText("Example Film") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("▶ Play").assertExists()
    }

    @Test
    fun openingTheFilmPagePollsTheServerOnceThenOnceMoreOnEveryStateChangeNotEveryProgressTick() {
        fixture.lanChunkProtocol.answer = LanSetStatus(total = null, chunksHeld = 1L, bytesHeld = 10L)
        openFilm()
        compose.waitForIdle()
        assertEquals(1, fixture.lanChunkProtocol.setStatusCalls, "one poll on open")

        // Ten progress ticks at the same Running state — a healthy wiring
        // recomposes on each one (the pill's own label reflects it) without
        // tearing down and rebuilding the server-line flow every time.
        for (i in 1..10) {
            fixture.filmPreloading.setState("film-1", TOTAL, FilmPreloadState.Running(i.toLong(), TOTAL))
            compose.waitForIdle()
        }
        assertEquals(2, fixture.lanChunkProtocol.setStatusCalls, "Running started once; no re-poll per progress tick with no time advanced")
    }

    @Test
    fun needsSpacesStorageLinkReachesStorageAndBackReturnsToTheFilm() {
        fixture.filmPreloading.setState("film-1", TOTAL, FilmPreloadState.NeedsSpace(TOTAL))
        openFilm()
        compose.onNodeWithText("Needs 5.0 GB · Try again").assertExists()

        compose.onNodeWithText("Raise the cache budget").performClick()
        compose.onNodeWithText("STORAGE", substring = true).assertExists()

        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("▶ Play").assertExists()
    }

    private companion object {
        const val TOTAL = 5 * 1_073_741_824L
    }
}
