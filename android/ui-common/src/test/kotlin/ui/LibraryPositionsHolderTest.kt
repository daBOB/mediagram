package ui

import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import catalog.MenuScreen
import catalog.ResolvedPosition
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [LibraryPositionsHolder] holds its six keys as one saveable
 * [catalog.LibraryPositions] value rather than six independent pieces of
 * Compose state. This pins the two things folding them together put at
 * risk: that the one value actually survives a real save/restore cycle —
 * the Activity destroyed and recreated from a `Bundle`, the same thing
 * process death does — with every key set, and that
 * [LibraryPositionsHolder.leaveFrom] still clears exactly what
 * [catalog.leave] decides once it is reading and writing that one value
 * instead of six.
 *
 * The branch priority and the back order [catalog.leave] itself decides are
 * pinned exhaustively in `feature:catalog`'s `LibraryPositionsResolveTest`;
 * this only needs enough of them to show the holder still carries them
 * through correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryPositionsHolderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<LibraryPositionsHolderTestActivity>

    private fun open() {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(LibraryPositionsHolderTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun allSixKeysSurviveARealSaveAndRestore() {
        open()
        compose.runOnUiThread {
            LibraryPositionsHolderTestActivity.holder.apply {
                setId = "set-1"
                titleId = "set-2"
                collection = "spartacus"
                season = "Season 1"
                listId = "list-1"
                menuScreen = MenuScreen.TmdbKey
            }
        }
        compose.waitForIdle()

        compose.runOnUiThread {
            val saved = Bundle()
            controller.saveInstanceState(saved).pause().stop().destroy()
            controller =
                Robolectric
                    .buildActivity(LibraryPositionsHolderTestActivity::class.java)
                    .create(saved)
                    .start()
                    .resume()
                    .visible()
        }
        compose.waitForIdle()

        val holder = LibraryPositionsHolderTestActivity.holder
        assertEquals("set-1", holder.setId)
        assertEquals("set-2", holder.titleId)
        assertEquals("spartacus", holder.collection)
        assertEquals("Season 1", holder.season)
        assertEquals("list-1", holder.listId)
        assertEquals(MenuScreen.TmdbKey, holder.menuScreen)
    }

    @Test
    fun leavingThePlayerClearsOnlyTheSetIdKey() {
        open()
        compose.runOnUiThread {
            LibraryPositionsHolderTestActivity.holder.apply {
                setId = "set-1"
                listId = "list-1"
            }
        }
        compose.waitForIdle()

        compose.runOnUiThread {
            LibraryPositionsHolderTestActivity.holder.leaveFrom(ResolvedPosition.Player("set-1"))
        }
        compose.waitForIdle()

        val holder = LibraryPositionsHolderTestActivity.holder
        assertNull(holder.setId)
        assertEquals("list-1", holder.listId)
    }

    @Test
    fun leavingAMenuScreenUncoversTheTitleItOpenedOver() {
        open()
        compose.runOnUiThread {
            LibraryPositionsHolderTestActivity.holder.apply {
                titleId = "set-1"
                menuScreen = MenuScreen.System
            }
        }
        compose.waitForIdle()

        compose.runOnUiThread {
            LibraryPositionsHolderTestActivity.holder.leaveFrom(ResolvedPosition.Menu(MenuScreen.System))
        }
        compose.waitForIdle()

        val holder = LibraryPositionsHolderTestActivity.holder
        assertNull(holder.menuScreen)
        assertEquals("set-1", holder.titleId)
    }
}
