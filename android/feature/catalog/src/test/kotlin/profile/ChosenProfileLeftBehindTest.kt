package catalog.profile

import data.DefaultHouseholdsRepository
import data.HouseholdsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * andre, watching here in Home, left behind as this device moves to Flat
 * ([HouseholdsOwnerHarness]'s install): whatever else goes wrong, the picker
 * shows rather than a wait that lasts until the app is left.
 */
class ChosenProfileLeftBehindTest : HouseholdsOwnerHarness() {
    /** The households read after a join fail while this is set; who is here still reads. */
    private var failing = false

    private suspend fun TestScope.flaky(): HouseholdsRepository {
        core.chosen = "a"
        repository.reload()
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        val read = households
        return object : HouseholdsRepository by read {
            override suspend fun reload() = if (failing) error("store busy") else read.reload()
        }
    }

    @Test
    fun aChosenProfileNoLongerHereIsThePicker() =
        runTest(UnconfinedTestDispatcher()) {
            core.chosen = "a"
            repository.reload()
            val picker = ProfileViewModel(repository, sync)
            backgroundScope.launch { picker.state.collect {} }
            assertEquals("andre", assertIs<ProfileUiState.Chosen>(picker.state.value).profile.name)
            // The device now shows Flat — a switch whose answer never came back — where andre is not.
            core.homes.joined = "flat"
            repository.reload()
            assertNull(repository.chosenProfileId.value)
            assertEquals(listOf("Bea", "Cy"), assertIs<ProfileUiState.Picking>(picker.state.value).profiles.map { it.name })
        }

    @Test
    fun aJoinSaysSoThoughTheHouseholdsCannotBeReadAfterIt() =
        runTest(UnconfinedTestDispatcher()) {
            val join = JoinHouseholdViewModel(repository, flaky())
            backgroundScope.launch { join.joined.collect { said += it } }
            join.pick("flat")
            failing = true
            join.enterPin("5678")
            assertNull(repository.chosenProfileId.value)
            // The switch took: the prompt closes for the picker, never saying it did not go through.
            assertNull(join.pin.value)
            assertEquals(listOf(false), said)
        }

    @Test
    fun theOwnersUseHereSaysSoThoughTheHouseholdsCannotBeReadAfterIt() =
        runTest(UnconfinedTestDispatcher()) {
            val owner = HouseholdsOwnerViewModel(repository, flaky(), sync)
            backgroundScope.launch { owner.joined.collect { said += it } }
            owner.open()
            owner.enterPin("1234")
            failing = true
            owner.useHere("flat")
            assertNull(repository.chosenProfileId.value)
            assertEquals(listOf(false), said)
        }
}
