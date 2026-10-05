package catalog.profile

import catalog.MainDispatcherRule
import data.DefaultWatchStateRepository
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.CatalogCoreProvider
import testing.FakeCore
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The picker's wait for a round: [round] is what landing one changes, then a re-read, as `DefaultWatchSync` makes until one has landed. */
private class OneRound(
    private val round: suspend () -> Unit,
) : WatchSync {
    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() = Unit

    override suspend fun awaitFirstRound() = round()
}

/**
 * A first profile only once this device has heard the household: before a
 * sync round lands, one made blind under a member's name would hand its PIN
 * to that member on every device. Over [FakeCore]'s rules, which refuse it
 * as the tablet's core does.
 */
class FirstProfileWaitsForASyncRoundTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core = FakeCore().apply { syncedOnce = false }
    private val repository = DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined)

    private fun picker(round: suspend () -> Unit = {}) = ProfileViewModel(repository, OneRound(round))

    /** [ProfileViewModel.state] is shared `WhileSubscribed`: something has to be watching it. */
    private fun TestScope.watch(vm: ProfileViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    private fun ProfileViewModel.picking() = state.value as ProfileUiState.Picking

    private fun ProfileViewModel.makeFirst(name: String) {
        createFirst(name)
        enterPin("1234")
        enterPin("1234")
    }

    @Test
    fun beforeAnyRoundNoFirstProfileIsOfferedAndTheCoreRefusesOne() =
        runTest {
            val vm = picker()
            watch(vm)
            val picking = vm.picking()
            assertTrue(picking.awaitingHousehold)
            assertFalse(picking.needsFirstProfile)
            assertFalse(picking.needsAdmin, "nobody to ask about")

            vm.makeFirst("Ann")

            assertEquals(HOUSEHOLD_NOT_HEARD, vm.picking().notice)
            assertTrue(core.profiles.isEmpty())
        }

    @Test
    fun aRoundThatFoundNobodyLetsANewHouseholdMakeItsFirst() =
        runTest {
            val vm =
                picker {
                    core.syncedOnce = true
                    repository.reload()
                }
            watch(vm)
            assertTrue(vm.picking().needsFirstProfile)
            assertFalse(vm.picking().awaitingHousehold)

            vm.makeFirst("Ann")

            assertTrue(core.profiles.single().admin)
        }

    @Test
    fun aRoundThatBroughtTheHouseholdShowsItsTilesNotAFirstProfile() =
        runTest {
            val vm =
                picker {
                    core.profiles = listOf(Profile("a", "andre", admin = true))
                    core.syncedOnce = true
                    repository.reload()
                }
            watch(vm)
            val picking = vm.picking()
            assertEquals(listOf("andre"), picking.profiles.map { it.name })
            assertFalse(picking.needsFirstProfile)
            assertFalse(picking.awaitingHousehold)
            assertFalse(picking.needsAdmin)
        }

    /** A first round that outlasts the picker's wait still brings the form in when it lands. */
    @Test
    fun aRoundLandingAfterThePickerShowedWaitingOffersTheFirstProfile() =
        runTest {
            val vm = picker()
            watch(vm)
            assertTrue(vm.picking().awaitingHousehold)

            core.syncedOnce = true
            repository.reload()

            assertTrue(vm.picking().needsFirstProfile)
        }
}
