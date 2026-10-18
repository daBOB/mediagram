package data

import kotlinx.coroutines.test.runTest
import model.Profile
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchStateForAnotherProfileTest {
    private val chosen = Profile("a", "Anna")
    private val other = Profile("k", "Kim")

    private fun fixture() = WatchStateFixture(profiles = listOf(chosen, other), chosen = "a")

    @Test
    fun progressForAnotherProfileIsThatProfiles() =
        runTest {
            val f = fixture()

            f.repository.setProgress("s1", 300.0, 2400.0, profileId = "k")

            assertEquals(listOf(300.0), f.core.snapshot("k").progress.map { it.at })
            assertTrue(f.core.snapshot("a").progress.isEmpty())
            assertTrue(f.repository.snapshot.value.progress.isEmpty())
        }

    @Test
    fun finishedForAnotherProfileIsThatProfiles() =
        runTest {
            val f = fixture()
            f.repository.setProgress("s1", 300.0, 2400.0, profileId = "k")

            f.repository.markFinished("s1", profileId = "k")

            val kim = f.core.snapshot("k")
            assertEquals(listOf("s1"), kim.watched.map { it.setId })
            assertTrue(kim.progress.isEmpty())
            assertTrue(f.core.snapshot("a").watched.isEmpty())
            assertTrue(f.repository.snapshot.value.watched.isEmpty())
        }

    @Test
    fun snapshotOfReadsTheNamedProfile() =
        runTest {
            val f = fixture()
            f.repository.setProgress("s1", 120.0, null, profileId = "k")

            assertEquals(listOf("s1"), f.repository.snapshotOf("k")?.progress?.map { it.setId })
            assertTrue(f.repository.snapshotOf("a")?.progress?.isEmpty() == true)
            assertNull(f.repository.snapshot.value.progress.firstOrNull())
        }

    @Test
    fun snapshotOfIsNullWhenTheCoreCannotSay() =
        runTest {
            val f = fixture()
            f.provider.beforeCore = { error("keystore unavailable") }

            assertNull(f.repository.snapshotOf("k"))
        }
}
