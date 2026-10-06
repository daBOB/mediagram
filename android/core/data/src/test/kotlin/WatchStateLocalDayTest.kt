package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.CoreInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import uniffi.mediagram_core.Profile as CoreProfile

class WatchStateLocalDayTest {
    @Test
    fun eachProgressWriteCountsOnThisDevicesDateAtTheMomentItLands() =
        runTest {
            val seeded =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    chosen = "p1"
                }
            val days = mutableListOf<String>()
            val core =
                object : CoreInterface by seeded {
                    override suspend fun setProgress(
                        profileId: String,
                        setId: String,
                        at: Double,
                        duration: Double?,
                        localDay: String,
                    ) {
                        days += localDay
                        seeded.setProgress(profileId, setId, at, duration, localDay)
                    }
                }
            var today = "2026-09-26"
            val repository = DefaultWatchStateRepository(FakeCoreProvider(core), Dispatchers.Unconfined) { today }
            repository.reload()

            repository.setProgress("set-1", 600.0, 5_400.0)
            // The title plays on past midnight: the next write counts on the new day.
            today = "2026-09-27"
            repository.setProgress("set-1", 610.0, 5_400.0)

            assertEquals(listOf("2026-09-26", "2026-09-27"), days)
            assertEquals(listOf(610.0), repository.snapshot.value.progress.map { it.at })
        }
}
