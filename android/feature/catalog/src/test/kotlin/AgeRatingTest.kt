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
    fun theKidsOwnLimitDecidesARatedTitle() {
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("0", 6))
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("6", 6))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("12", 6))
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("12", 12))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("16", 12))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("18", 12))
    }

    @Test
    fun anythingThatIsNotABareAgeIsUnrated() {
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf(null, 12))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("", 12))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("PG-13", 12))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("FSK 12", 12))
        assertEquals(KidsVerdict.UNRATED, kidsVerdictOf("120", 12))
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
    fun aKidAtTwelveSeesRatedTwelveOrUnderAndEveryHandMark() {
        val sets = listOf(
            rated("Zero", "0"), rated("Six", "6"), rated("Twelve", "12"),
            rated("Sixteen", "16"), rated("Eighteen", "18"),
            rated("Unrated", null), rated("FromTwelve", null), rated("FromSix", null), rated("SixteenMarked", "16"),
            rated("lesson-1", null, Kind.TUTORIAL), rated("lesson-2", null, Kind.TUTORIAL),
        )
        val marks = mapOf("FromTwelve" to 12, "FromSix" to 6, "SixteenMarked" to 12, "lesson-2" to 12)
        assertEquals(
            listOf("Zero", "Six", "Twelve", "FromTwelve", "FromSix", "lesson-2"),
            forKidsProfile(sets, marks, 12).map { it.setId },
        )
    }

    @Test
    fun aKidAtSixSeesRatedSixOrUnderAndOnlyMarksFromSix() {
        val sets = listOf(rated("Six", "6"), rated("Twelve", "12"), rated("FromTwelve", null), rated("FromSix", null))
        // A mark on a rated title never counts: "Twelve" marked from 6 stays out at 6.
        val marks = mapOf("FromTwelve" to 12, "FromSix" to 6, "Twelve" to 6)
        assertEquals(listOf("Six", "FromSix"), forKidsProfile(sets, marks, 6).map { it.setId })
    }
}
