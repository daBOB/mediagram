package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Profile
import testing.FakeCore
import testing.ResolvedCoreProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.mediagram_core.ProfileOutcome
import uniffi.mediagram_core.Profile as CoreProfile

/** [WatchStateRepository.chosenProfile]: who is watching, answered by the repository rather than by each screen. */
class WatchStateChosenProfileTest {
    private val core =
        FakeCore().apply {
            profiles = listOf(CoreProfile("a", "Ana"), CoreProfile("k", "Mia", kids = true))
        }
    private val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

    @Test
    fun reloadPublishesTheChosenProfileWithItsKidsFlagAndLimit() =
        runTest {
            core.chosen = "k"

            repository.reload()

            assertEquals(Profile("k", "Mia", kids = true, kidsAge = 12), repository.chosenProfile.value)
        }

    @Test
    fun choosingAnotherProfileMovesTheChosenProfileWithIt() =
        runTest {
            core.chosen = "k"
            repository.reload()

            repository.chooseProfile("a")

            assertEquals(Profile("a", "Ana"), repository.chosenProfile.value)
        }

    /** A profile the core made after the last reload is the chosen profile once listed and chosen. */
    @Test
    fun aProfileMadeSinceTheLastReloadIsTheChosenProfileOnceChosen() =
        runTest {
            repository.reload()
            assertEquals(ProfileOutcome.Done, core.claimAdmin("a", "1234"))
            assertEquals(ProfileOutcome.Done, core.createKid("a", "1234", "Bea", 12u))
            repository.reload()
            val created = repository.profiles.value.single { it.name == "Bea" }

            repository.chooseProfile(created.id)

            assertEquals(created, repository.chosenProfile.value)
        }

    @Test
    fun aChosenProfileRemovedOnTheCoreLeavesNobodyChosen() =
        runTest {
            assertEquals(ProfileOutcome.Done, core.claimAdmin("a", "1234"))
            core.chosen = "k"
            repository.reload()

            assertEquals(ProfileOutcome.Done, core.deleteProfile("a", "1234", "k"))
            repository.reload()

            assertNull(repository.chosenProfile.value)
        }

    @Test
    fun anAccountResetForgetsWhoWasWatching() =
        runTest {
            core.chosen = "k"
            repository.reload()

            repository.invalidate()

            assertNull(repository.chosenProfile.value)
        }
}
