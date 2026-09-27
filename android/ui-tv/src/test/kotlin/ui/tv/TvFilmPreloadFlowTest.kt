package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
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
import playback.FilmPreloadState
import ui.tv.catalog.set

/**
 * A film's own Preload control, over the real [TvLibrary] walk rather than
 * [TvTitlePage] built directly — [TvAppFixture]'s own fixture used a
 * relaxed mock that never left `Idle`, so the NeedsSpace → Settings ›
 * Storage route was never actually exercised through it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvFilmPreloadFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private val film = set("film-1", Kind.MOVIE, "A Film", addedAt = 0, year = 2021, durationSecs = 9000).copy(totalBytes = TOTAL)

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", listOf(film))
            fixture.filmPreloading.setState(film.setId, TOTAL, FilmPreloadState.NeedsSpace(TOTAL))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("A Film")).fetchSemanticsNodes().isNotEmpty() }
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
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun needsSpacesStorageRowReachesSettingsAndBackReturnsToPlay() {
        compose.onNode(hasText("A Film") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("▶ Play").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("Raise the cache budget").assertIsFocused()
        compose.onNodeWithText("Raise the cache budget").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("STORAGE", substring = true).assertExists()

        back()
        compose.onNodeWithText("▶ Play").assertIsFocused()
    }

    private companion object {
        const val TOTAL = 5 * 1_073_741_824L
    }
}
