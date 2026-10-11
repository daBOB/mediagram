package ui.tv.system

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.MenuScreen
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.MenuActions
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest

/** With no rail on screen the menu lists the rail's seven rows; with the rail it lists none of them, as before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvMenuPageRailRowsTest : TvScreenStateTest() {
    private var chosen: RailItem? = null

    private fun menu(
        railItems: List<RailItem>,
        restoreKey: String? = null,
    ) = show {
        TvMenuPage(
            menu = MenuActions(onSystem = {}, onSettings = {}, onUpdate = {}, onTmdbKey = {}, onStartOver = {}),
            restoreKey = restoreKey,
            watchingAs = "Ada",
            onSwitchProfile = {},
            railItems = railItems,
            onRailItem = { chosen = it },
            onBack = {},
        )
    }

    @Test
    fun withTheRailOnScreenTheMenuListsNoneOfItsRows() {
        menu(railItems = emptyList())
        compose.onNodeWithText("Latest").assertDoesNotExist()
    }

    @Test
    fun withNoRailEachOfItsRowsIsChosenFromTheMenu() {
        menu(railItems = RailItem.entries)
        for (item in RailItem.entries) {
            compose.onNodeWithText(item.label).performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(item, chosen)
        }
    }

    @Test
    fun backFromSettingsLandsOnItsOwnRow() {
        menu(railItems = RailItem.entries, restoreKey = menuRestoreKey(MenuScreen.Settings))
        compose.onNodeWithText("Settings").assertIsFocused()
    }
}
