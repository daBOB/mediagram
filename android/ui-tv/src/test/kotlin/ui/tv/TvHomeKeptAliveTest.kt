package ui.tv

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.MediaSet
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.films
import ui.tv.catalog.home.HOLD_MS
import ui.tv.catalog.home.TvHomeCover

/**
 * [TvLibrary]'s root, kept composed and laid out under a pushed frame
 * rather than rebuilt on every Back: focus can never reach a node inside
 * it while something else covers it, its own text never doubles up with
 * what that frame shows, and Back still lands the remote right back where
 * it left — a poster's own round trip here; the debugger's freeze and the
 * box's own memory and frame-timing numbers are the device walk's job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeKeptAliveTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
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

    @Test
    fun aTitlePageNeverLetsFocusReachTheHiddenHomeRootAndBackRestoresItsPoster() {
        press(plate("Film 1"))
        compose.onNodeWithText("Film 1").assertExists()
        // Home's own poster is hidden, not merely unfocused — only the
        // title page's own heading answers to "Film 1" while it covers Home.
        compose.onAllNodesWithText("Film 1").assertCountEquals(1)

        val dpad = listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        repeat(10) { index ->
            key(dpad[index % dpad.size])
            compose.onNode(isFocused() and hasAnyAncestor(hasTestTag(TvHomeLayerTestTag))).assertDoesNotExist()
        }

        back()
        plate("Film 1").assertIsFocused()
    }

    private fun plate(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}

/**
 * [TvHomeCover]'s own rotation, paused for as long as [LocalLibraryCovered]
 * reads true and picked back up the moment it turns false — the box's own
 * playback-memory bound is the device walk's job; this is the part a
 * Robolectric clock proves on its own, over two films with no shuffle or
 * editorial pick to make non-deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeCoverRotationWhileCoveredTest : TvScreenStateTest() {
    @Test
    fun rotationHoldsWhileCoveredAndPicksBackUpOnceUncovered() {
        val filmA = film("film-a", "Film A")
        val filmB = film("film-b", "Film B")
        val covered = mutableStateOf(false)
        show {
            CompositionLocalProvider(LocalLibraryCovered provides covered.value) {
                TvHomeCover(
                    films = listOf(filmA, filmB),
                    watchlist = emptySet(),
                    initialFilmId = filmA.setId,
                    onPlay = {},
                    onOpenTitle = {},
                    onToggleWatchlist = { _, _ -> },
                    arrivalFocus = remember { FocusRequester() },
                    upExit = remember { FocusRequester() },
                )
            }
        }
        compose.onNodeWithText("FILM A").assertExists()

        compose.runOnUiThread { covered.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(HOLD_MS * 3)
        compose.waitForIdle()
        compose.onNodeWithText("FILM A").assertExists()

        compose.runOnUiThread { covered.value = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(HOLD_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithText("FILM B").assertExists()
    }

    private fun film(
        id: String,
        title: String,
    ) = MediaSet(
        setId = id,
        kind = Kind.MOVIE,
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
    )
}
