package playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GIB = 1024L * 1024 * 1024

private fun volume(id: String, free: Long = 10 * GIB) =
    CacheVolume(id = id, label = id, dir = File("/mnt/$id/mlib"), freeBytes = free, removable = id != INTERNAL_VOLUME_ID)

private val internal = volume(INTERNAL_VOLUME_ID)
private val card = volume("card")
private val volumes = listOf(internal, card)

class CacheLocationTest {

    @Test
    fun aChosenVolumeThatIsPresentIsUsedAsIs() {
        val location = resolveCacheLocation(volumes, chosenId = "card")

        assertEquals(card, location.volume)
        assertFalse(location.fellBack)
    }

    @Test
    fun aChosenVolumeThatIsAbsentFallsBackToInternal() {
        val location = resolveCacheLocation(volumes, chosenId = "missing-card")

        assertEquals(internal, location.volume)
        assertTrue(location.fellBack)
    }

    @Test
    fun nothingChosenUsesInternalWithoutCountingItAsAFallback() {
        val location = resolveCacheLocation(volumes, chosenId = null)

        assertEquals(internal, location.volume)
        assertFalse(location.fellBack)
    }

    @Test
    fun capIsFreeSpacePlusWhatIsAlreadyHeldLessTheReserve() {
        assertEquals(29 * GIB, budgetCap(volume("x", free = 10 * GIB), heldBytes = 20 * GIB))
    }

    @Test
    fun capNeverFallsBelowTheFloor() {
        assertEquals(MIN_CACHE_BYTES, budgetCap(volume("x", free = 0), heldBytes = 0))
    }

    @Test
    fun ladderDoublesFromTheFloorAndStopsAtOrBelowTheCap() {
        assertEquals(listOf(MIN_CACHE_BYTES, 1 * GIB, 2 * GIB), budgetLadder(cap = 2 * GIB))
    }

    @Test
    fun ladderAlwaysContainsAtLeastTheFloor() {
        assertEquals(listOf(MIN_CACHE_BYTES), budgetLadder(cap = 0))
    }

    @Test
    fun ladderStopsShortOfACapThatIsNotAPowerOfTwoStep() {
        assertEquals(listOf(MIN_CACHE_BYTES, 1 * GIB), budgetLadder(cap = 1500 * 1024 * 1024))
    }
}
