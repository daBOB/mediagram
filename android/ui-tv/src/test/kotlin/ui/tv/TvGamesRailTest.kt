package ui.tv

import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
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
import ui.tv.catalog.films
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The rail's Games row over the real [TvLibrary]: it opens game night as a
 * full-screen page showing the host's TV page, Back from a running game
 * stays on the page, and Back from the lobby leaves and stops the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGamesRailTest {
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
    fun gamesOnTheRailOpensTheGamePage() {
        press(compose.onNodeWithContentDescription("Games"))

        assertEquals("http://127.0.0.1:8080/tv", shownUrl())
    }

    @Test
    fun backInTheLobbyLeavesAndStopsTheHost() {
        fixture.gameCore.gameNightBackAnswer = false
        press(compose.onNodeWithContentDescription("Games"))
        shownUrl()

        back()

        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, fixture.gameCore.gameNightStops)
    }

    @Test
    fun backDuringAGameStaysOnThePage() {
        fixture.gameCore.gameNightBackAnswer = true
        press(compose.onNodeWithContentDescription("Games"))
        shownUrl()

        back()

        assertEquals("http://127.0.0.1:8080/tv", shownUrl())
        assertEquals(0, fixture.gameCore.gameNightStops)
    }

    /** The URL the page's WebView loaded, waiting for the host to answer first. */
    private fun shownUrl(): String? {
        compose.waitUntil(timeoutMillis = 5_000) { webView() != null }
        return shadowOf(assertNotNull(webView())).lastLoadedUrl
    }

    private fun webView(): WebView? = controller.get().window.decorView.findWebView()

    private fun android.view.View.findWebView(): WebView? =
        when (this) {
            is WebView -> this
            is android.view.ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
            else -> null
        }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
