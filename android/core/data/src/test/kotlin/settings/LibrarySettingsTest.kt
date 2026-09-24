package settings

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySettingsTest {
    @Test
    fun selectionsIncludesTheInitialChoiceAndSuccessfulChanges() =
        runTest {
            val settings = InMemoryLibrarySettings()
            val choices = mutableListOf<String?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                settings.selections().collect { choices += it }
            }

            settings.write("first")
            settings.write("first")
            settings.write("second")
            settings.clear()

            assertEquals(listOf(null, "first", "second", null), choices)
        }

    @Test
    fun aChosenLibraryReadsBackAsChosen() =
        runTest {
            val settings = InMemoryLibrarySettings()

            settings.write("a1b2c3")

            assertEquals("a1b2c3", settings.read())
        }

    /**
     * Nothing chosen and something chosen have to be distinguishable: the
     * first is the whole reason the picker comes up.
     */
    @Test
    fun nothingChosenYetReadsBackAsNothing() =
        runTest {
            assertNull(InMemoryLibrarySettings().read())
        }

    @Test
    fun startingOverLeavesNoLibraryChosen() =
        runTest {
            val settings = InMemoryLibrarySettings()
            settings.write("a1b2c3")

            settings.clear()

            assertNull(settings.read())
        }
}
