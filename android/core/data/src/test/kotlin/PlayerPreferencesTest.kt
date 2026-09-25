package data

import kotlinx.coroutines.test.runTest
import uniffi.mediagram_core.PreferenceRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A core whose preference calls are backed by an in-memory list, the same
 * shape [WatchStateRepositoryTest]'s `StateCoreClient` uses: this
 * repository filters and writes back what it read, which `FakeCore`'s
 * fixed answers cannot stand in for.
 */
private class PreferenceCoreClient : CoreClient by FakeCore() {
    private val rows = mutableListOf<PreferenceRow>()

    override suspend fun preferences(profileId: String): List<PreferenceRow> = rows.toList()

    override suspend fun setPreference(profileId: String, scope: String, name: String, value: String?): Boolean {
        rows.removeAll { it.scope == scope && it.name == name }
        if (value != null) rows += PreferenceRow(scope, name, value)
        return true
    }
}

class PlayerPreferencesTest {

    @Test
    fun loadFiltersToTheAskedScope() = runTest {
        val core = PreferenceCoreClient()
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(core))
        core.setPreference("p1", "key:tmdb-tv-1399", "speed", "1.5")
        core.setPreference("p1", "set:01FILM", "speed", "1")

        val loaded = preferences.load("p1", "key:tmdb-tv-1399")

        assertEquals(mapOf("speed" to "1.5"), loaded)
    }

    @Test
    fun nothingRememberedForAScopeAnswersAnEmptyMap() = runTest {
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(PreferenceCoreClient()))

        assertEquals(emptyMap(), preferences.load("p1", "set:01FILM"))
    }

    @Test
    fun rememberingNullForgetsTheChoice() = runTest {
        val core = PreferenceCoreClient()
        val preferences = DefaultPlayerPreferences(ResolvedCoreProvider(core))
        preferences.remember("p1", "show:Geldhochschule", "speed", "1.5")
        assertEquals(mapOf("speed" to "1.5"), preferences.load("p1", "show:Geldhochschule"))

        val forgot = preferences.remember("p1", "show:Geldhochschule", "speed", null)

        assertTrue(forgot)
        assertEquals(emptyMap(), preferences.load("p1", "show:Geldhochschule"))
    }
}
