package ui.settings

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import model.DailyLimitWords
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.COLLAPSED
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The owner's Add someone forms keep what was typed and chosen until the add
 * went through, as the web's do, then close; and an add's refusal, never
 * another change's, is said under its household. Over
 * [HouseholdsOwnerHarness]'s people: Home (andre and Bea) offers Belongs to;
 * Flat's Cy has a name already taken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerViewAddsTest : HouseholdsOwnerHarness() {
    private fun chip(text: String, group: String): SemanticsMatcher = hasText(text) and isSelectable() and hasAnyAncestor(hasContentDescription(group))

    private val age = "Age limit for the new kid"
    private val daily = "${DailyLimitWords.LABEL} for the new kid"
    private val failed = "That did not go through. Please try again."

    /** Home's Add someone open as a kid's form, filled in: Bea's, FSK 12, 45 minutes a day, named [name]; then Add a kid. */
    private fun addBeasKid(name: String) {
        openAdd("Home")
        inPart("Home", hasText(HouseholdWords.KID) and isSelectable()).performScrollTo().performClick()
        inPart("Home", hasText("Bea") and isSelectable()).performScrollTo().performClick()
        inPart("Home", chip("FSK 12", age)).performScrollTo().performClick()
        inPart("Home", chip("45 min", daily)).performScrollTo().performClick()
        add("Home", addKid, ADD_A_KID, name)
    }

    private fun assertKidFormKept(name: String) {
        inPart("Home", addKid).assert(hasText(name))
        inPart("Home", hasText("Bea") and isSelectable()).assertIsSelected()
        inPart("Home", chip("FSK 12", age)).assertIsSelected()
        inPart("Home", chip("45 min", daily)).assertIsSelected()
    }

    @Test
    fun aTakenNameKeepsTheKidFormAsItWas() {
        openAs(owner)
        addBeasKid("Cy")
        pin("1234")
        assertKidFormKept("Cy")

        // The retry is the kid that was meant: Bea's, with the limits chosen; then the form closes and starts over.
        add("Home", addKid, ADD_A_KID, "Mia")
        val mia = made("Mia")
        assertEquals(listOf("b", 12, 45), listOf(mia.parentId, mia.kidsAge?.toInt(), mia.dailyLimit?.toInt()))
        addSomeone("Home").assert(hasStateDescription(COLLAPSED))
        openAdd("Home")
        inPart("Home", hasText(HouseholdWords.KID) and isSelectable()).performScrollTo().performClick()
        inPart("Home", addKid).assert(!hasText("Mia"))
        inPart("Home", hasText("andre") and isSelectable()).assertIsSelected()
    }

    @Test
    fun aDismissedOwnersPinKeepsTheKidForm() {
        openAs(owner)
        addBeasKid("Mia")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        assertKidFormKept("Mia")

        inPart("Home", hasText(ADD_A_KID) and hasClickAction()).performScrollTo().performClick()
        pin("1234")
        assertEquals("b" to 12, made("Mia").parentId to made("Mia").kidsAge?.toInt())
    }

    @Test
    fun aRefusedGrownUpKeepsItsName() {
        openAs(owner)
        add("Barn", addGrownUp, ADD_A_GROWN_UP, "Cy")
        pin("1234")
        pin("4321")
        pin("4321")
        inPart("Barn", addGrownUp).assert(hasText("Cy"))
    }

    /** An add never sent — its new PIN dismissed — leaves the next change's refusal to the page's end. */
    @Test
    fun anotherChangeAfterAnAbandonedAddIsSaidAtTheEnd() {
        openAs(owner)
        add("Barn", addGrownUp, ADD_A_GROWN_UP, "Eve")
        pin("1234")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()

        household.provider.beforeCore = { throw IllegalStateException("the core is away") }
        opened("Bea")
        compose.onNode(hasText("Anime") and hasAnyAncestor(hasContentDescription("Sections for Bea"))).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, ys(hasText(failed)).size)
        assertEquals(0, countIn("Barn", hasText(failed)))
        val create = ys(createOpener).single()
        assertTrue(ys(hasText(failed)).single() > create, "expected the refusal at the page's end")
    }
}
