package ui.tv.player

import android.os.Bundle
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import model.Kind
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.set

/**
 * A settings panel saved open and restored before the player is built:
 * with nothing to draw it over it is closed rather than left open and
 * invisible, so Back is not spent closing a panel nobody can see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSettingsRestoreTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixtures = mutableListOf<TvPlayerFixture>()
    private var restored: ActivityController<TvPlayerTestActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread {
            restored?.close()
            fixtures.forEach(TvPlayerFixture::close)
        }
    }

    @Test
    fun aPanelRestoredWithoutAPlayerIsClosedAndBackLeaves() {
        val saved = Bundle()
        compose.runOnUiThread {
            TvPlayerTestActivity.set = set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
            TvPlayerTestActivity.fixture = TvPlayerFixture().also(fixtures::add)
        }
        val first = compose.runOnUiThread { Robolectric.buildActivity(TvPlayerTestActivity::class.java).setup().visible() }
        compose.waitForIdle()
        for (key in listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionCenter)) {
            compose.onNode(isFocused()).performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
        compose.runOnUiThread {
            first.saveInstanceState(saved)
            first.pause().stop().destroy()
        }

        compose.runOnUiThread {
            TvPlayerTestActivity.fixture = TvPlayerFixture(playerReady = false).also(fixtures::add)
            restored = Robolectric.buildActivity(TvPlayerTestActivity::class.java).setup(saved).visible()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TvSettingsPanelTag).assertDoesNotExist()

        compose.runOnUiThread { restored!!.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Library").assertExists()
    }
}
