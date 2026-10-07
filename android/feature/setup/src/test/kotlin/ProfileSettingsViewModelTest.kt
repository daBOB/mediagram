package setup

import data.PlayerPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreHandle
import testing.MainDispatcherRule
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class MemoryPreferences(
    val stored: MutableMap<Triple<String, String, String>, String> = mutableMapOf(),
    private val accepts: Boolean = true,
) : PlayerPreferences {
    override suspend fun load(
        profileId: String,
        scope: String,
    ): Map<String, String> = stored.filterKeys { it.first == profileId && it.second == scope }.mapKeys { it.key.third }

    override suspend fun remember(
        profileId: String,
        scope: String,
        name: String,
        value: String?,
    ): Boolean {
        if (!accepts) return false
        if (value == null) stored.remove(Triple(profileId, scope, name)) else stored[Triple(profileId, scope, name)] = value
        return true
    }
}

class ProfileSettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private suspend fun fixture(chosen: String?): SetupFixture {
        val raw = FakeCore(authorized = true)
        val core =
            object : FakeCoreHandle by raw {
                override suspend fun profiles() = listOf(Profile("alice", "Alice"))

                override suspend fun chosenProfile() = chosen
            }
        return SetupFixture(core = raw, build = { core }).signedIn().also { it.watchState.reload() }
    }

    @Test
    fun noProfileChosenShowsNobodyAndRefusesAWrite() =
        runTest {
            val fixture = fixture(chosen = null)
            val preferences = MemoryPreferences()
            val model = ProfileSettingsViewModel(fixture.watchState, preferences)

            model.chooseSubtitle("de")

            assertNull(model.profile.value)
            assertEquals("Nobody chosen", profileStatus(model.profile.value))
            assertEquals("off", model.subtitle.value)
            assertEquals(emptyMap(), preferences.stored)
        }

    @Test
    fun aChoiceIsStoredUnderTheProfileScopeAndShown() =
        runTest {
            val fixture = fixture(chosen = "alice")
            val preferences = MemoryPreferences()
            val model = ProfileSettingsViewModel(fixture.watchState, preferences)

            model.chooseSubtitle("de")

            assertEquals(mapOf(Triple("alice", "profile", "subtitle") to "de"), preferences.stored)
            assertEquals("de", model.subtitle.value)
            assertEquals("Alice", profileStatus(model.profile.value))
        }

    /** The web's Settings line: a kid's own limit, never a fixed one. */
    @Test
    fun aKidsStatusNamesItsOwnLimit() {
        assertEquals("Mia · Kids · FSK 6", profileStatus(model.Profile("k", "Mia", kids = true, kidsAge = 6)))
        assertEquals("Old · Kids · FSK 12", profileStatus(model.Profile("o", "Old", kids = true)))
    }

    @Test
    fun aValueOutsideTheOfferedSetIsNeverWritten() =
        runTest {
            val fixture = fixture(chosen = "alice")
            val preferences = MemoryPreferences()
            val model = ProfileSettingsViewModel(fixture.watchState, preferences)

            model.chooseSubtitle("fr")

            assertEquals(emptyMap(), preferences.stored)
            assertEquals("off", model.subtitle.value)
        }

    @Test
    fun aStoredChoiceIsReadBackAndAnUnknownOneIgnored() =
        runTest {
            val known = MemoryPreferences(mutableMapOf(Triple("alice", "profile", "subtitle") to "en"))
            assertEquals("en", ProfileSettingsViewModel(fixture("alice").watchState, known).subtitle.value)

            val stale = MemoryPreferences(mutableMapOf(Triple("alice", "profile", "subtitle") to "xx"))
            assertEquals("off", ProfileSettingsViewModel(fixture("alice").watchState, stale).subtitle.value)
        }

    @Test
    fun aRefusedWriteLeavesTheRowWhereItWas() =
        runTest {
            val fixture = fixture(chosen = "alice")
            val model = ProfileSettingsViewModel(fixture.watchState, MemoryPreferences(accepts = false))

            model.chooseSubtitle("en")

            assertEquals("off", model.subtitle.value)
        }
}
