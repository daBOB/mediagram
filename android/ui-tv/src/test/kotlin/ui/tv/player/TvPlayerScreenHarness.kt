package ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import model.Kind
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import player.PlayerUiState
import ui.tv.catalog.set
import kotlin.test.assertEquals

/**
 * What every [TvPlayerScreen] test opens on: the screen over a
 * [TvPlayerFixture], playing an episode with a runtime, and the two ways a
 * test drives it — a key to whatever holds the remote, and Back through the
 * dispatcher. [fixture] is made by [makeFixture], so a test class can open
 * the same screen for a different viewer.
 */
abstract class TvPlayerScreenHarness {
    @get:Rule val compose = createEmptyComposeRule()
    internal lateinit var fixture: TvPlayerFixture
    internal lateinit var controller: ActivityController<TvPlayerTestActivity>

    internal open fun makeFixture() = TvPlayerFixture()

    /** The run the title is opened on; none, unless a test class needs one. */
    internal open val run: List<String> = emptyList()

    @Before
    fun openPlayer() {
        compose.runOnUiThread {
            fixture = makeFixture()
            TvPlayerTestActivity.fixture = fixture
            TvPlayerTestActivity.run = run
            TvPlayerTestActivity.switches.clear()
            TvPlayerTestActivity.set =
                set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                    .copy(season = 1)
            controller = Robolectric.buildActivity(TvPlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        assertEquals(PlayerUiState.Playing, controller.get().playerViewModel.state.value)
    }

    @After
    fun closePlayer() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
        }
    }

    internal fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /**
     * One press of a held key as the window delivers it: [repeat] is how
     * many presses of the same hold came before this one, which is what a
     * remote's auto-repeat reports and what a test's own key presses never do.
     */
    internal fun keyDown(
        keyCode: Int,
        repeat: Int,
    ) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, repeat)) }
        compose.waitForIdle()
    }

    internal fun keyUp(keyCode: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)) }
        compose.waitForIdle()
    }

    /** From the controls as they open, on play/pause: across to the gear, and pressed. */
    internal fun openSettings() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
    }

    /** Back through the dispatcher alone, as a gesture delivers it: no key for focus to take first. */
    internal fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Back as a remote sends it: a key, down and up, through the window —
     * where focus sees it before the dispatcher does and could take it as
     * a move out of whatever holds the remote.
     */
    internal fun pressBackKey() {
        keyDown(KeyEvent.KEYCODE_BACK, 0)
        keyUp(KeyEvent.KEYCODE_BACK)
    }
}
