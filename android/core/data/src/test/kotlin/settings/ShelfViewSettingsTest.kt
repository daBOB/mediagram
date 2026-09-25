package settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfViewSettingsTest {
    @Test
    fun nothingStoredIsPosters() {
        assertEquals(ShelfView.GRID, shelfViewFrom(null))
    }

    @Test
    fun aStoredListReadsBackAsTheList() {
        assertEquals(ShelfView.LIST, shelfViewFrom("list"))
    }

    /** A value some later version wrote must still leave a shelf to look at. */
    @Test
    fun anythingUnrecognisedIsTheDefault() {
        assertEquals(ShelfView.GRID, shelfViewFrom("carousel"))
        assertEquals(ShelfView.GRID, shelfViewFrom("grid"))
    }

    @Test
    fun aChoiceIsWhatTheSettingThenSays() {
        val settings = InMemoryShelfViewSettings()
        settings.choose(ShelfView.LIST)
        assertEquals(ShelfView.LIST, settings.view.value)
    }
}
