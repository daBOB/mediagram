package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.ProfileUiState
import designsystem.Overscan
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.TvTheme
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TvProfilePicker] over plain state: which state draws what, how wide the
 * tiles are, and — out of touch mode, where a remote always is — what holds
 * the remote first and where the D-pad takes it from there.
 * `TvProfilePickerTest` checks the first focus on a real window manager too.
 */
// A television-sized window, not Robolectric's own narrow default: the tile
// row is a LazyRow now (see TvProfilePicker), which only composes semantics
// nodes for tiles that actually land inside the measured viewport — a
// narrower window would drop the trailing tile from the tree these tests
// query, not just fail to show it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp")
class TvProfilePickerStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val calls = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun chosenRendersNothing() {
        show(ProfileUiState.Chosen(Profile(id = "ada", name = "Ada")))
        compose.onNodeWithText("Who's watching?").assertDoesNotExist()
    }

    @Test
    fun pickingShowsTheHeadingAndEveryProfilesName() {
        show(
            ProfileUiState.Picking(
                // Ada runs the household: with nobody running it her name would also be a claim above the tiles.
                profiles = listOf(Profile(id = "ada", name = "Ada", admin = true), Profile(id = "bea", name = "Bea", kids = true)),
                canStay = false,
            ),
        )
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Bea").assertExists()
    }

    @Test
    fun aKidsTileNamesItsOwnLimit() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("m", "Mia", kids = true, kidsAge = 6), Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Kids · FSK 6").assertExists()
        compose.onNodeWithText("Kids · FSK 12").assertExists()
        compose.onNodeWithText("KIDS").assertDoesNotExist()
    }

    /** No grown-up: the first profile is made here, and holds the remote; the kids' tiles stay, and there is nobody to manage as yet. */
    @Test
    fun aDeviceWithNoGrownUpOffersTheFirstProfileAndNoManage() {
        leaveTouchMode()
        show(ProfileUiState.Picking(listOf(Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Create the first profile — it runs this household").assertIsFocused()
        compose.onNodeWithText("TV kids").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertDoesNotExist()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("  Ann ")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("first Ann"), calls)
    }

    /**
     * No sync round landed yet: a first profile made blind under a household
     * member's name would take over that member's PIN, so none is offered —
     * the waiting line, and Try again holding the remote.
     */
    @Test
    fun aDeviceNotYetSyncedWaitsForTheHouseholdWithTryAgain() {
        leaveTouchMode()
        show(ProfileUiState.Picking(emptyList(), canStay = false, synced = false))
        compose.onNodeWithText("Waiting for this household’s profiles…").assertExists()
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("retry"), calls)
    }

    /** A round that found nobody: a new household's first profile holds the remote, with nothing to wait for. */
    @Test
    fun aNewHouseholdOffersItsFirstProfileAndNoTryAgain() {
        leaveTouchMode()
        show(ProfileUiState.Picking(emptyList(), canStay = false))
        compose.onNodeWithText("Create the first profile — it runs this household").assertIsFocused()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun grownUpsWithNoAdminAreAskedWhoRunsTheHouseholdAboveTheTiles() {
        leaveTouchMode()
        show(ProfileUiState.Picking(listOf(Profile("a", "andre"), Profile("b", "Bo"), Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Who runs this household?").assertExists()
        compose.onNodeWithTag(tvClaimTag("o")).assertDoesNotExist()
        compose.onNodeWithTag(TvManageProfilesTag).assertExists()
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(tvClaimTag("a")).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("claim b"), calls)
    }

    @Test
    fun aHouseholdWithAnAdminHasTilesManageAndTheHonestNote() {
        leaveTouchMode()
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Who runs this household?").assertDoesNotExist()
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
        compose.onNodeWithText("it is not a login", substring = true).assertExists()
        compose.onNodeWithText("They are not a login", substring = true).assertDoesNotExist()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("choose o", "manage"), calls)
    }

    @Test
    fun aRefusalIsSaidAboveTheTiles() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true)), canStay = false, notice = "That is not allowed."))
        compose.onNodeWithText("That is not allowed.").assertExists()
    }

    /** Back from a PIN or from Manage: the remote lands on what it left from, not on the first tile. */
    @Test
    fun theRemoteReturnsToWhatItLeftFrom() {
        leaveTouchMode()
        val household = ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("b", "Bo"), Profile("o", "TV kids", kids = true)), canStay = false)
        show(household, landing = TvPickerSpot.Tile("b"))
        compose.onNodeWithTag("tv-profile-tile-b").assertIsFocused()
        show(household, landing = TvPickerSpot.Manage)
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
        // Somewhere no longer there — a claim once the household has its admin: the first tile.
        show(household, landing = TvPickerSpot.Claim("b"))
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
    }

    /** The question, the tiles, Manage, the note and Stay are more than a 540dp television shows at once: the remote still reaches Stay. */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun everythingOnATallPickerIsReachedByWalkingDown() {
        leaveTouchMode()
        show(ProfileUiState.Picking(listOf(Profile("a", "andre"), Profile("o", "TV kids", kids = true)), canStay = true))
        press(Key.DirectionDown)
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Stay as I am").assertIsFocused()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue(compose.onNodeWithText("Stay as I am").getBoundsInRoot().bottom <= screen.bottom, "Stay is off the screen")
        press(Key.DirectionCenter)
        assertEquals(listOf("stay"), calls)
    }

    @Test
    fun anErrorShowsAboveTheTilesWithATryAgainRow() {
        show(ProfileUiState.Picking(profiles = emptyList(), canStay = false, error = "Could not load profiles. Please try again."))
        compose.onNodeWithText("Could not load profiles. Please try again.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
        // Whether a household exists is not known: nothing is offered to be made.
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
    }

    @Test
    fun canStayOffersNoStayRowAndCanStayOnDoes() {
        show(ProfileUiState.Picking(profiles = emptyList(), canStay = false))
        compose.onNodeWithText("Stay as I am").assertDoesNotExist()

        show(ProfileUiState.Picking(profiles = emptyList(), canStay = true))
        compose.onNodeWithText("Stay as I am").assertExists()
    }

    /**
     * Four profiles on a 960dp television — the box this was seen on — all
     * fit inside the overscan-safe width, the way the phone's picker shows
     * every profile at once.
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun fourProfilesFitInsideTheSafeWidthOfA960dpTelevision() {
        show(
            ProfileUiState.Picking(
                profiles = listOf("andre", "test", "TV test", "TV kids").map { Profile(id = it, name = it, kids = it == "TV kids") },
                canStay = true,
            ),
        )
        val screen = compose.onRoot().getBoundsInRoot()

        val first = compose.onNodeWithTag(TvProfilePickerFirstTileTag).getBoundsInRoot()
        val last = compose.onNodeWithTag("tv-profile-tile-TV kids").getBoundsInRoot()
        assertTrue(first.left >= screen.left + Overscan.horizontal - Slack, "first tile starts at ${first.left}")
        assertTrue(last.right <= screen.right - Overscan.horizontal + Slack, "last tile ends at ${last.right}")
    }

    @Test
    fun tilesNarrowToFitTheRowButNoFurtherThanTheirFloor() {
        val room = 864.dp
        val gap = 16.dp
        // Few enough: full width.
        assertEquals(180.dp, profileTileWidth(count = 3, room = room, gap = gap))
        // Five: narrowed so all five and their gaps fit exactly.
        val five = profileTileWidth(count = 5, room = room, gap = gap)
        assertTrue(five * 5 + gap * 4 <= room + 0.01.dp, "five tiles of $five")
        // Many: held at the floor, and the row scrolls instead.
        assertEquals(140.dp, profileTileWidth(count = 9, room = room, gap = gap))
    }

    private fun leaveTouchMode() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun show(
        state: ProfileUiState,
        landing: TvPickerSpot? = null,
    ) {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                TvTheme {
                    TvProfilePicker(
                        state = state,
                        onChoose = { calls += "choose $it" },
                        onStay = { calls += "stay" },
                        onRetry = { calls += "retry" },
                        onClaim = { calls += "claim $it" },
                        onCreateFirst = { calls += "first $it" },
                        onManage = { calls += "manage" },
                        landing = landing,
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}

/** Rounding between pixels and dp. */
private val Slack = 1.dp
