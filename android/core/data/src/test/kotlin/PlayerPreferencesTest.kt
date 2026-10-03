package data

import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A [FakeCore] holding profile `p1`: preferences, like the real table's rows, are only kept for a profile that exists. */
private fun coreWithProfile() = FakeCore().apply { profiles = listOf(Profile("p1", "Ada", false)) }

class PlayerPreferencesTest {

    @Test
    fun loadFiltersToTheAskedScope() = runTest {
        val core = coreWithProfile()
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(core))
        core.setPreference("p1", "key:tmdb-tv-1399", "speed", "1.5")
        core.setPreference("p1", "set:01FILM", "speed", "1")

        val loaded = preferences.load("p1", "key:tmdb-tv-1399")

        assertEquals(mapOf("speed" to "1.5"), loaded)
    }

    @Test
    fun nothingRememberedForAScopeAnswersAnEmptyMap() = runTest {
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(coreWithProfile()))

        assertEquals(emptyMap(), preferences.load("p1", "set:01FILM"))
    }

    @Test
    fun rememberingNullForgetsTheChoice() = runTest {
        val core = coreWithProfile()
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(core))
        preferences.remember("p1", "show:Geldhochschule", "speed", "1.5")
        assertEquals(mapOf("speed" to "1.5"), preferences.load("p1", "show:Geldhochschule"))

        val forgot = preferences.remember("p1", "show:Geldhochschule", "speed", null)

        assertTrue(forgot)
        assertEquals(emptyMap(), preferences.load("p1", "show:Geldhochschule"))
    }
}
