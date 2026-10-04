package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Profile
import testing.FakeCore
import testing.ResolvedCoreProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.mediagram_core.Profile as CoreProfile

/** [WatchStateRepository.chosenProfile]: who is watching, answered by the repository rather than by each screen. */
class WatchStateChosenProfileTest {
    private val core =
        FakeCore().apply {
            profiles = listOf(CoreProfile("a", "Ana"), CoreProfile("k", "Mia", kids = true))
        }
    private val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

    @Test
    fun reloadPublishesTheChosenProfileWithItsKidsFlag() =
        runTest {
            core.chosen = "k"

            repository.reload()

            assertEquals(Profile("k", "Mia", kids = true), repository.chosenProfile.value)
        }

    @Test
    fun choosingAnotherProfileMovesTheChosenProfileWithIt() =
        runTest {
            core.chosen = "k"
            repository.reload()

            repository.chooseProfile("a")

            assertEquals(Profile("a", "Ana"), repository.chosenProfile.value)
        }

    /** The setup flow's path: a profile made on this device is chosen before any reload lists it. */
    @Test
    fun aProfileCreatedHereIsTheChosenProfileOnceChosen() =
        runTest {
            val created = repository.createProfile("Bea", kids = true)!!

            repository.chooseProfile(created.id)

            assertEquals(created, repository.chosenProfile.value)
        }

    @Test
    fun deletingTheChosenProfileLeavesNobodyChosen() =
        runTest {
            core.chosen = "k"
            repository.reload()

            repository.deleteProfile("k")

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
