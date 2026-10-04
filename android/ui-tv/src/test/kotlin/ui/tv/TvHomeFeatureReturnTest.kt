package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.films

/**
 * Home's feature cards are stops a title is opened from like any other, so
 * Back from that title lands on the card — not on the cover's Watch now,
 * Home's arrival with nothing to return to, nor on the bar's Home pill.
 * Every film has a backdrop, so the cover is drawn above the cards and the
 * card is not also where a fresh arrival would land anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeFeatureReturnTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            val pictured = films(4).map { it.copy(backdropPath = "/nowhere/${it.setId}.jpg") }
            fixture =
                TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", pictured, watch = { setEditorsChoice("film-0", true) })
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(EditorsChoice).fetchSemanticsNodes().isNotEmpty() }
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
    fun backFromTheEditorsChoiceLandsOnItsCard() {
        val card = compose.onNode(EditorsChoice)
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        press(card)
        compose.onNodeWithText("▶ Play").assertIsFocused()

        back()

        card.assertIsFocused()
    }

    /** The same walk the way the box takes it: out of touch mode, the remote's own keys. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theRemotesOwnWalkBackFromTheEditorsChoiceLandsOnItsCard() {
        compose.runOnUiThread { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
        val card = compose.onNode(EditorsChoice)
        repeat(4) { if (card.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }.not()) key(KeyEvent.KEYCODE_DPAD_DOWN) }
        card.assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("▶ Play").assertIsFocused()

        key(KeyEvent.KEYCODE_BACK)

        card.assertIsFocused()
    }

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

    private companion object {
        val EditorsChoice = hasText("EDITOR'S CHOICE") and hasClickAction()
    }
}
