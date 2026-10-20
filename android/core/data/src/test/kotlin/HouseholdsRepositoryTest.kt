package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.Household
import model.Households
import model.Profile
import testing.FakeCoreProvider
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The repository over [testing.FakeCore], beside a real watch-state
 * repository whose profiles and first round it re-reads on. It has its own
 * [provider], so a test can fail its reads and not the profiles'.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdsRepositoryTest {
    private val fixture = WatchStateFixture(listOf(Profile("a", "Ada", admin = true, owner = true)))
    private val core = fixture.core
    private val provider = FakeCoreProvider(core)

    private fun TestScope.households() = DefaultHouseholdsRepository(provider, fixture.repository, backgroundScope, Dispatchers.Unconfined)

    private fun DefaultHouseholdsRepository.names() = view.value.list.map { it.name }

    @Test
    fun readsWhatTheCoreSays() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.names["flat"] = "Flat"
            core.profiles += CoreProfile("b", "Bo", admin = true, household = "flat")

            assertEquals(
                Households(
                    joined = HOME_HOUSEHOLD,
                    list = listOf(Household("flat", "Flat", "b"), Household(HOME_HOUSEHOLD, "Home", "a")),
                    everyone = listOf(Profile("a", "Ada", admin = true, owner = true), Profile("b", "Bo", admin = true, household = "flat")),
                    synced = true,
                ),
                households().view.value,
            )
        }

    @Test
    fun rereadsWhenTheProfilesChange() =
        runTest(UnconfinedTestDispatcher()) {
            val households = households()
            core.profiles += CoreProfile("b", "Bo")
            assertEquals(listOf("Ada"), households.view.value.everyone.map { it.name })

            fixture.repository.reload()

            assertEquals(listOf("Ada", "Bo"), households.view.value.everyone.map { it.name })
        }

    @Test
    fun rereadsWhenTheFirstRoundLands() =
        runTest(UnconfinedTestDispatcher()) {
            core.syncedOnce = false
            fixture.repository.reload()
            val households = households()
            core.homes.names["flat"] = "Flat"
            assertEquals(listOf("Home"), households.names())
            assertEquals(false, households.view.value.synced)

            core.syncedOnce = true
            // The same profiles: only the round is news.
            fixture.repository.reload()

            assertEquals(listOf("Flat", "Home"), households.names())
            assertEquals(true, households.view.value.synced)
        }

    /** Home is always live, so a view with no household at all is not a read: the last one stays. */
    @Test
    fun aViewWithNoHouseholdIsNotPublished() =
        runTest(UnconfinedTestDispatcher()) {
            val households = households()
            val before = households.view.value
            core.homes.names.clear()

            households.reload()

            assertEquals(before, households.view.value)
        }

    @Test
    fun keepsTheLastViewWhenAReadFails() =
        runTest(UnconfinedTestDispatcher()) {
            val households = households()
            val before = households.view.value
            provider.beforeCore = { error("keystore unavailable") }
            core.profiles += CoreProfile("b", "Bo")

            // Its own re-read fails quietly.
            fixture.repository.reload()
            assertEquals(before, households.view.value)

            // Asked directly, the failure is the caller's to see.
            assertFailsWith<IllegalStateException> { households.reload() }
            assertEquals(before, households.view.value)
        }
}
