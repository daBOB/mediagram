package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import model.KidsVerdict
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.PlayerMarksState
import testing.WatchStateFixture
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Kids control in the player — the web's select (`player-library-marks.js`)
 * as a menu: on an unrated title, not for kids, from 6 or from 12; on a rated
 * one, what its rating decided and nothing to press; on a kid's own profile, nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerKidsChoiceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var fixture: PlayerLifecycleFixture? = null
    private lateinit var player: ActivityController<PlayerTestActivity>
    private lateinit var plain: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread {
            if (::player.isInitialized) player.close()
            if (::plain.isInitialized) plain.close()
            fixture?.close()
        }
    }

    private fun play() {
        compose.runOnUiThread {
            val opened = PlayerLifecycleFixture(WatchStateFixture())
            fixture = opened
            PlayerTestActivity.fixture = opened
            player = Robolectric.buildActivity(PlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    private fun show(marks: PlayerMarksState) {
        compose.runOnUiThread {
            plain = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            plain.get().setContent {
                MaterialTheme { PlayerMarks(marks, PlayerMarksActions(onToggleWatchlist = {}, onKidsMark = {}, onSetInList = { _, _ -> }, onCreateList = {})) }
            }
        }
        compose.waitForIdle()
    }

    private fun marks(verdict: KidsVerdict, canMarkKids: Boolean = true) =
        PlayerMarksState(
            watchlisted = false,
            kidsMark = null,
            lists = emptyList(),
            memberOf = emptySet(),
            kidsVerdict = verdict,
            ageLabel = if (verdict == KidsVerdict.UNRATED) null else "FSK 16",
            canMarkKids = canMarkKids,
        )

    @Test fun anUnratedTitleIsMarkedFromSixAndThenNotForKids() {
        play()
        val repository = fixture!!.repository
        compose.onNodeWithText("Not for kids").performClick()
        compose.onNodeWithText("From 12").assertExists()
        compose.onNodeWithText("From 6").performClick()
        compose.waitForIdle()
        assertEquals(6, repository.snapshot.value.kidsMarks["set-one"])

        compose.onNodeWithText("From 6").performClick()
        compose.onNodeWithText("Not for kids").performClick()
        compose.waitForIdle()
        assertNull(repository.snapshot.value.kidsMarks["set-one"])
        compose.onNodeWithText("Not for kids").assertExists()
    }

    @Test fun aRatedTitleShowsItsVerdictWithNothingToPress() {
        show(marks(KidsVerdict.UNSAFE))
        compose.onNodeWithText("FSK 16 · not for kids").assertIsNotEnabled()
    }

    @Test fun aKidsOwnProfileHasNoKidsControl() {
        show(marks(KidsVerdict.UNRATED, canMarkKids = false))
        compose.onNodeWithText("Not for kids").assertDoesNotExist()
        compose.onNodeWithText("My List").assertExists()
    }
}
