package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvDailyLimitTag
import ui.tv.profile.tvKidsLimitTag
import ui.tv.profile.tvManagerTileTag
import kotlin.test.assertEquals

/**
 * Manage profiles over the picker by remote alone ([TvProfilesHarness]):
 * Who's managing?, a grown-up's PIN, then their calm rows — and Manage
 * closing, its PIN forgotten, when the app is left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageFlowTest : TvProfilesHarness() {
    /**
     * Manage opened from the masthead, then the box left — Home, a remote
     * left alone. Back in the library, the next one to choose who is
     * watching meets the picker, not that grown-up's Manage, and the PIN
     * Manage held changes nothing any more.
     */
    @Test
    fun leavingTheAppClosesManageAndForgetsItsPin() {
        launch(listOf(andre, bo, cy), chosen = "a")
        reopenPicker()
        openManageAs("b", "5678")

        leaveAndReturn()
        awaitLibrary()
        reopenPicker()

        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("As Bo").assertCountEquals(0)
        compose.runOnUiThread { fixture.manage.setKidsAge("c", 12) }
        compose.waitForIdle()
        assertEquals(6, fixture.core.profiles.single { it.id == "c" }.kidsAge?.toInt(), "a change went through on a PIN nobody gave again")
    }

    @Test
    fun theAdminResetsAPinAndRemovesAGrownUpWithItsKid() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs("a", "1234")
        compose.onNode(rowOf("andre")).assertIsFocused()

        walkTo(rowOf("Bo"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(hasText("Reset PIN") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("A new PIN for Bo").assertExists()
        pin("4321")
        pin("4321")
        compose.onNodeWithText("As andre").assertExists()
        assertEquals("4321", fixture.core.roles.pins["b"])

        walkTo(rowOf("Bo"), Key.DirectionDown)
        press(Key.DirectionCenter)
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Remove Bo, their kids, and everything they have watched?").assertExists()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onAllNodesWithText("Bo").assertCountEquals(0)
        assertEquals(listOf("a"), fixture.core.profiles.map { it.id })

        walkTo(rowOf("Done"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
        compose.onAllNodesWithText("Cy").assertCountEquals(0)
    }

    @Test
    fun aGrownUpAddsAKidAtSixAndRaisesItsLimit() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs("b", "5678")
        compose.onNode(rowOf("Bo")).assertIsFocused()
        compose.onNode(rowOf("Cy")).assert(hasText("Kid · Bo · FSK 6"))

        // Bo adds no grown-up: Add someone is a kid at once.
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Lina")
        compose.onNodeWithTag(tvKidsLimitTag(6)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(tvDailyLimitTag(null)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina")).assert(hasText("Kid · Bo · FSK 6"))

        walkTo(rowOf("Lina"), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina")).assert(hasText("Kid · Bo · FSK 12"))
        val lina = fixture.core.profiles.single { it.name == "Lina" }
        assertEquals(12, lina.kidsAge?.toInt())
        assertEquals("b", lina.parentId)
    }

    @Test
    fun aNameAlreadyTakenIsSaidOnThePanel() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs("a", "1234")
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf(HouseholdWords.KID), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("cy")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("That name is already used on this install.").assertExists()
        assertEquals(3, fixture.core.profiles.size)
    }

    /** A wrong PIN is said on the pad and opens nothing; Back is the tiles again, the remote on the one tried, and Back again the picker. */
    @Test
    fun aWrongPinOnWhoIsManagingKeepsAsking() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        press(Key.DirectionCenter)
        pin("0000")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        back()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithTag(tvManagerTileTag("a")).assertIsFocused()
        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
    }

    @Test
    fun aParentHidesASectionFromItsKidAndTheRemoteStaysOnTheSwitch() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs("b", "5678")
        walkTo(rowOf("Cy"), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf("Sections"), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf("Anime"), Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("anime"), fixture.core.profiles.single { it.id == "c" }.hiddenSections)
        compose.onNode(rowOf("Anime")).assertIsFocused().assertIsOff()
        walkTo(rowOf("Done"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Cy")).assertIsFocused().assert(hasText("Kid · Bo · FSK 6 · hides Anime"))
    }
}
