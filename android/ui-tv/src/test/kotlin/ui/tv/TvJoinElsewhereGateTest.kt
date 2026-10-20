package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.lifecycle.ViewModelProvider
import catalog.profile.JoinHouseholdViewModel
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeCore
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * A box whose household has no grown-up left, by remote alone
 * ([TvProfilesHarness]): Kim, a kid, is here in Home; andre, the owner, is in
 * Flat, so Join a household stands where the first profile would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvJoinElsewhereGateTest : TvProfilesHarness() {
    private val andreElsewhere: FakeCore.() -> Unit = {
        profiles = listOf(CoreProfile("a", "andre", admin = true, owner = true, household = "flat"), CoreProfile("k", "Kim", kids = true))
        homes.names["flat"] = "Flat"
    }

    /** andre moved to [household] on another device, and the households read again here. */
    private fun andreMovesTo(household: String) {
        compose.runOnUiThread {
            fixture.core.profiles = fixture.core.profiles.map { if (it.id == "a") it.copy(household = household) else it }
            ViewModelProvider(fixture)[JoinHouseholdViewModel::class.java].reread()
        }
        compose.waitForIdle()
    }

    /** Join a household closed by a grown-up coming back stays closed when they go again: only a press opens it. */
    @Test
    fun joinAHouseholdOpensOnlyWhenPressed() {
        launch(emptyList(), chosen = null, seed = andreElsewhere)
        compose.onNode(rowOf(HouseholdWords.JOIN_A_HOUSEHOLD)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Flat")).assertIsFocused()

        andreMovesTo(HOME_HOUSEHOLD)
        compose.onAllNodes(rowOf("Flat")).assertCountEquals(0)
        andreMovesTo("flat")
        compose.onAllNodes(rowOf("Flat")).assertCountEquals(0)
        compose.onNode(rowOf(HouseholdWords.JOIN_A_HOUSEHOLD)).assertExists()
    }
}
