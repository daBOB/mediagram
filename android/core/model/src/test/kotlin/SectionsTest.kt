package model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which sections a profile is not shown — the web's `hiddenSectionsOf`, before the catalog filters by it. */
class SectionsTest {
    @Test
    fun theOwnerHidesNothingWhateverTheirListSays() {
        val owner = Profile("a", "andre", admin = true, owner = true, hiddenSections = listOf("anime"))
        assertEquals(emptySet(), owner.hiddenFrom)
    }

    @Test
    fun aHouseholdsAdminSeesWhatTheOwnerLeftThem() {
        val celine = Profile("c", "Celine", admin = true, household = "madani", hiddenSections = listOf("anime"))
        assertEquals(setOf("anime"), celine.hiddenFrom)
    }

    @Test
    fun anyoneElseHidesWhatTheirListNames() {
        assertEquals(setOf("movies"), Profile("m", "Maja", hiddenSections = listOf("movies")).hiddenFrom)
        assertEquals(setOf("anime"), Profile("k", "Mia", kids = true, hiddenSections = listOf("anime")).hiddenFrom)
        assertEquals(emptySet(), Profile("b", "Ben").hiddenFrom)
    }
}
