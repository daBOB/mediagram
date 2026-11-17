package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.DONE
import catalog.profile.EVERYONE_HERE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.managingAs
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.tvManagerTileTag
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/**
 * The owner's one view on television, by remote alone ([TvProfilesHarness]) over [twoHouseholds]:
 * andre, the owner, runs Home with Bo; Dan runs Flat. The owner's Manage profiles row opens it at
 * once; on a device showing another household the owner's tile, ending Who's managing?, opens it
 * after the owner's PIN. What an add sends is read back from the core: where the new profile
 * landed, and whose it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvOwnerViewTest : TvProfilesHarness() {
    private val home = "Home · Admin andre · 2 profiles"
    private val flat = "Flat · Admin Dan · 1 profile"

    /** Settings › Profiles as andre, walked down to Manage profiles. */
    private fun ownersPage() {
        launch(emptyList(), chosen = "a", seed = twoHouseholds())
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf(MANAGE_PROFILES), Key.DirectionDown)
    }

    private fun openOwnersView() {
        ownersPage()
        press(Key.DirectionCenter)
    }

    private fun assertFocusedOn(text: String) = compose.onNode(isFocused()).assert(hasText(text))

    /** The next Add someone down, then Kid. */
    private fun addAKidBelow() {
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf(HouseholdWords.KID), Key.DirectionDown)
        press(Key.DirectionCenter)
    }

    @Test
    fun ownerPageHasOneManageRowAndNoHouseholdsRow() {
        ownersPage()
        compose.onAllNodes(rowOf(MANAGE_PROFILES)).assertCountEquals(1)
        compose.onAllNodesWithText(EVERYONE_HERE).assertCountEquals(0)
        compose.onAllNodes(rowOf(HouseholdWords.HOUSEHOLDS)).assertCountEquals(0)
        compose.onAllNodes(rowOf(HouseholdWords.OWNER)).assertCountEquals(0)
    }

    /** This device's household first: its header, a row per person with their line, one Add someone; then the next; then Create a household. No PIN just to look. */
    @Test
    fun ownerViewListsEveryHouseholdWithHeaderPeopleAndAdds() {
        openOwnersView()
        compose.onNodeWithText(managingAs("andre")).assertExists()
        compose.onAllNodesWithText(HouseholdWords.WHOS_MANAGING).assertCountEquals(0)
        compose.onAllNodesWithText(HouseholdWords.OWNERS_PIN).assertCountEquals(0)
        val rows =
            listOf(
                home to null, "andre" to "Owner · Admin · PIN set", "Bo" to "Grown-up · PIN set", HouseholdWords.ADD_SOMEONE to null,
                flat to null, "Dan" to "Admin · No PIN", HouseholdWords.ADD_SOMEONE to null,
                HouseholdWords.CREATE_A_HOUSEHOLD to null, DONE to null,
            )
        rows.forEachIndexed { index, (row, line) ->
            if (index > 0) press(Key.DirectionDown)
            assertFocusedOn(row)
            line?.let(::assertFocusedOn)
        }
    }

    /** Flat's Add someone, Grown-up, makes them in Flat — the owner's PIN asked at this first change — and the remote is back on Flat's header. */
    @Test
    fun addGrownUpUnderAHouseholdSendsThatHousehold() {
        openOwnersView()
        walkTo(rowOf(flat), Key.DirectionDown)
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        name("Eve")
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")
        compose.onNodeWithText("A PIN for Eve").assertExists()
        pin("2468")
        pin("2468")

        assertEquals("flat", fixture.core.profiles.single { it.name == "Eve" }.household)
        compose.onNode(rowOf("Flat · Admin Dan · 2 profiles")).assertIsFocused()
    }

    /** Home has two grown-ups: Belongs to comes first, on its admin; the kid made for Bo is Bo's. Flat has one, so asks nothing. */
    @Test
    fun addKidWithTwoGrownUpsAsksBelongsToFirst() {
        openOwnersView()
        addAKidBelow()
        compose.onNodeWithText(HouseholdWords.BELONGS_TO).assertExists()
        compose.onNode(rowOf("andre")).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Mia")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        pin("1234")

        val mia = fixture.core.profiles.single { it.name == "Mia" }
        assertEquals("b", mia.parentId)
        assertEquals(HOME_HOUSEHOLD, mia.household)
        compose.onNode(rowOf("Home · Admin andre · 3 profiles")).assertIsFocused()
        // Bo's kid in the owner's own household is Bo's to change: listed, not a row.
        compose.onNodeWithText("Kid · Bo · FSK 6").assertExists()
        compose.onAllNodes(rowOf("Mia")).assertCountEquals(0)

        walkTo(rowOf(flat), Key.DirectionDown)
        addAKidBelow()
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertExists()
        compose.onAllNodesWithText(HouseholdWords.BELONGS_TO).assertCountEquals(0)
    }

    /** Back walks out a level at a time: from a household's actions to its header, from the view to the row that opened it. */
    @Test
    fun backFromAHouseholdsActionsReturnsToItsHeader() {
        openOwnersView()
        walkTo(rowOf(flat), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf(HouseholdWords.RENAME)).assertIsFocused()

        back()
        compose.onNode(rowOf(flat)).assertIsFocused()
        back()
        compose.onNode(rowOf(MANAGE_PROFILES)).assertIsFocused()
    }

    /** A device showing Flat, Dan watching: the owner's tile ends Who's managing?; its PIN opens the same view, this device's household first, and that PIN is the only one asked. */
    @Test
    fun ownersTileOnAnotherHouseholdsDeviceOpensTheOwnerView() {
        launch(emptyList(), chosen = "d", seed = twoHouseholds(joined = "flat"))
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf(MANAGE_PROFILES), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(tvManagerTileTag("d")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvManagerTileTag("a")).assertIsFocused().assert(hasText(HouseholdWords.OWNER))
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")

        compose.onNodeWithText(managingAs("andre")).assertExists()
        compose.onNode(rowOf(flat)).assertIsFocused()
        compose.onNode(rowOf(home)).assertExists()

        addAKidBelow()
        name("Kai")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onAllNodesWithText(HouseholdWords.OWNERS_PIN).assertCountEquals(0)
        assertEquals("d", fixture.core.profiles.single { it.name == "Kai" }.parentId)
    }
}
