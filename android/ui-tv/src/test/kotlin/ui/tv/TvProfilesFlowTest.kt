package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.TvProfilePickerFirstTileTag
import ui.tv.profile.tvClaimTag
import ui.tv.profile.tvDailyLimitTag
import ui.tv.profile.tvKidsLimitTag
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The picker, its PINs and Manage profiles, by remote alone ([TvProfilesHarness]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvProfilesFlowTest : TvProfilesHarness() {
    @Test
    fun aDeviceWithNoGrownUpMakesTheFirstProfileWithAPinTypedTwice() {
        launch(listOf(Profile("k", "TV kids", kids = true)), chosen = null, pins = emptyMap())
        compose.onNodeWithText("Create the first profile — it runs this household").assertIsFocused()
        press(Key.DirectionCenter)
        name("Ann")
        compose.onNodeWithText("A PIN for Ann").assertExists()
        pin("2468")
        compose.onNodeWithText("The new PIN again").assertExists()
        pin("2468")

        compose.onAllNodesWithText("Create the first profile — it runs this household").assertCountEquals(0)
        compose.onNode(hasText("Ann") and hasClickAction()).assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertExists()
        val ann = fixture.core.profiles.single { it.name == "Ann" }
        assertTrue(ann.admin, "the first profile runs the household")
        assertEquals("2468", fixture.core.roles.pins[ann.id])
    }

    @Test
    fun aKidsTileOpensWithoutAPin() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasText("Cy") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        // In, as Cy: the library filtered to Cy's own limit, and no PIN was asked.
        awaitLibrary("Nothing rated FSK 6 or under yet.")
        assertEquals("c", fixture.core.chosen)
    }

    @Test
    fun aGrownUpsTileOpensOnlyOnItsOwnPin() {
        launch(listOf(andre, bo, cy), chosen = null)
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("andre’s PIN").assertExists()
        pin("1111")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        pin("1234")
        awaitLibrary()
    }

    @Test
    fun tooManyWrongPinsMakeTheProfileWait() {
        launch(listOf(andre, bo, cy), chosen = null)
        press(Key.DirectionCenter)
        // The remote's own digit keys, as a remote that has them types.
        repeat(6) { listOf(Key.Nine, Key.Nine, Key.Nine, Key.Nine).forEach(::press) }
        compose.onNodeWithText("Too many wrong PINs. Try again in 60 s.").assertExists()
        pin("1234")
        compose.onNodeWithText("Too many wrong PINs. Try again in 60 s.").assertExists()
    }

    @Test
    fun aHouseholdWithNoAdminIsAskedWhoRunsItOnce() {
        launch(listOf(andre.copy(admin = false), bo, cy), chosen = null)
        press(Key.DirectionUp)
        compose.onNodeWithTag(tvClaimTag("a")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("andre’s PIN").assertExists()
        pin("1234")
        compose.onAllNodesWithText("Who runs this household?").assertCountEquals(0)
        assertTrue(fixture.core.profiles.single { it.id == "a" }.admin)
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
    }

    /** Back on the PIN gives up the PIN only — the reopened picker's own Back ("Stay as I am") is the next press. */
    @Test
    fun backOnThePinGivesUpThePinNotThePicker() {
        launch(listOf(andre, bo, cy), chosen = "a")
        reopenPicker()
        compose.onNodeWithText("Stay as I am").assertExists()
        walkTo(hasText("Bo") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Bo’s PIN").assertExists()

        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNode(hasText("Bo") and hasClickAction()).assertIsFocused()

        back()
        awaitLibrary()
        assertFalse(controller.get().isFinishing, "Back left the app")
    }

    /** "Stay as I am" goes back to the department the picker was opened from, not Home, with the remote on the avatar that opened it. */
    @Test
    fun stayingAsIAmKeepsTheChosenDepartment() {
        launch(listOf(andre, bo, cy), chosen = "a")
        awaitLibrary()
        compose.onNode(hasText("Movies") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasText("Movies") and isSelected()).assertExists()

        reopenPicker()
        back()
        awaitLibrary()

        compose.onNode(hasText("Movies") and isSelected()).assertExists()
        compose.onNode(hasContentDescription("Menu, watching as andre")).assertIsFocused()
    }

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
        openManageAs(rowOf("Bo"), "5678")

        leaveAndReturn()
        awaitLibrary()
        reopenPicker()

        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("As Bo").assertCountEquals(0)
        compose.runOnUiThread { fixture.manage.setKidsAge("c", 12) }
        compose.waitForIdle()
        assertEquals(6, fixture.core.profiles.single { it.id == "c" }.kidsAge?.toInt(), "a change went through on a PIN nobody gave again")
    }

    /** A first entry of a new PIN is not kept for whoever opens the app next. */
    @Test
    fun leavingTheAppDropsAHalfTypedNewPin() {
        launch(listOf(andre, bo, cy), chosen = null, pins = mapOf("a" to "1234"))
        walkTo(hasText("Bo") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        pin("5678")
        compose.onNodeWithText("The new PIN again").assertExists()

        leaveAndReturn()
        compose.onAllNodesWithText("The new PIN again").assertCountEquals(0)
        compose.onNodeWithText("Who's watching?").assertExists()
    }

    @Test
    fun theAdminResetsAPinAndRemovesAGrownUpWithItsKid() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("andre"), "1234")
        compose.onNode(rowOf("Bo")).assertIsFocused()

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
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
        compose.onAllNodesWithText("Cy").assertCountEquals(0)
        back()
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
    }

    @Test
    fun aGrownUpAddsAKidAtSixAndRaisesItsLimit() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("Bo"), "5678")
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        compose.onNode(rowOf("Cy · FSK 6 · No limit")).assertIsFocused()

        walkTo(rowOf("Add a kid"), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Lina")
        compose.onNodeWithTag(tvKidsLimitTag(6)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(tvDailyLimitTag(null)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina · FSK 6 · No limit")).assertExists()

        walkTo(rowOf("Lina · FSK 6 · No limit"), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina · FSK 12 · No limit")).assertExists()
        val lina = fixture.core.profiles.single { it.name == "Lina" }
        assertEquals(12, lina.kidsAge?.toInt())
        assertEquals("b", lina.parentId)
    }

    @Test
    fun aNameAlreadyTakenIsSaidOnThePanel() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("andre"), "1234")
        walkTo(rowOf("Add a kid"), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("cy")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("That name is already used on this install.").assertExists()
        assertEquals(3, fixture.core.profiles.size)
    }

    @Test
    fun aWrongPinOnWhoAreYouKeepsAsking() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        repeat(2) { press(Key.DirectionCenter) }
        compose.onNodeWithText("Who are you?").assertExists()
        press(Key.DirectionCenter)
        pin("0000")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        back()
        compose.onNodeWithText("Who are you?").assertExists()
        back()
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
        back()
        compose.onNodeWithText("Who's watching?").assertExists()
    }

    @Test
    fun aParentHidesASectionFromItsKidAndTheRemoteStaysOnTheSwitch() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("Bo"), "5678")
        press(Key.DirectionCenter)
        walkTo(rowOf("Sections"), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf("Anime"), Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("anime"), fixture.core.profiles.single { it.id == "c" }.hiddenSections)
        compose.onNode(rowOf("Anime")).assertIsFocused().assertIsOff()
        walkTo(rowOf("Done"), Key.DirectionDown)
        press(Key.DirectionCenter)
        back()
        compose.onNodeWithText("Kid · Bo · FSK 6 · hides Anime").assertExists()
    }
}
