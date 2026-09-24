package catalog

import model.KidsVerdict
import model.ageLabelOf
import model.ageOf
import model.kidsVerdictOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The rules in `model/AgeRating.kt` — the web player's `age-rating.js`,
 * which `age-rating.test.ts` holds to the same limits.
 */
class AgeRatingTest {
    @Test
    fun twelveAndYoungerIsForKidsAndOlderIsNot() {
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("0"))
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("12"))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("16"))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("18"))
    }

    @Test
    fun anythingThatIsNotABareAgeIsUnrated() {
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf(null))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf(""))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("PG-13"))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("FSK 12"))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("120"))
    }

    @Test
    fun theLabelNamesTheSystemAndTrimsWhatTheProviderWrote() {
        assertEquals(12, ageOf(" 12 "))
        assertEquals("FSK 6", ageLabelOf("6"))
        assertNull(ageLabelOf(null))
    }
}
