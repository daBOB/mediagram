package catalog

import model.Kind
import model.KidsVerdict
import model.MediaSet
import model.ageLabelOf
import model.ageOf
import model.forKidsProfile
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

    private fun rated(
        id: String,
        fsk: String?,
        kind: Kind = Kind.MOVIE,
    ) = MediaSet(
        setId = id, kind = kind, title = id, show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = null,
        durationSecs = null, posterPath = null, totalBytes = 0, fsk = fsk,
    )

    @Test
    fun aKidsProfileSeesRatedForKidsOrMarkedByHandAndNothingElse() {
        val sets = listOf(
            rated("Zero", "0"), rated("Six", "6"), rated("Twelve", "12"),
            rated("Sixteen", "16"), rated("Eighteen", "18"),
            rated("Unrated", null), rated("UnratedMarked", null), rated("SixteenMarked", "16"),
            rated("lesson-1", null, Kind.TUTORIAL), rated("lesson-2", null, Kind.TUTORIAL),
        )
        val marked = setOf("UnratedMarked", "SixteenMarked", "lesson-2")
        assertEquals(
            listOf("Zero", "Six", "Twelve", "UnratedMarked", "lesson-2"),
            forKidsProfile(sets, marked).map { it.setId },
        )
    }
}
