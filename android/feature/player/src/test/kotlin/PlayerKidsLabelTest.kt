package player

import model.KidsVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the Kids control says — the web player's `refreshKids` in `player-library-marks.js`. */
class PlayerKidsLabelTest {
    private fun marks(
        verdict: KidsVerdict,
        label: String? = null,
        kidsMark: Int? = null,
        forEveryKid: Boolean = false,
    ) = PlayerMarksState(
        watchlisted = false,
        kidsMark = kidsMark,
        lists = emptyList(),
        memberOf = emptySet(),
        kidsVerdict = verdict,
        ageLabel = label,
        forEveryKid = forEveryKid,
    )

    /** Read against the widest limit: FSK 12 is for some kids, FSK 6 for every one. */
    @Test
    fun aRatedTitleSaysWhatItsRatingDecided() {
        assertEquals("For kids · FSK 6", kidsLabel(marks(KidsVerdict.SAFE, "FSK 6", forEveryKid = true)))
        assertEquals("For kids from 12 · FSK 12", kidsLabel(marks(KidsVerdict.SAFE, "FSK 12")))
        assertEquals("FSK 16 · not for kids", kidsLabel(marks(KidsVerdict.UNSAFE, "FSK 16")))
    }

    /** The web shows an unrated title's control as its select's current option. */
    @Test
    fun anUnratedTitleShowsItsChoice() {
        assertEquals("Not for kids", kidsLabel(marks(KidsVerdict.UNRATED)))
        assertEquals("From 6", kidsLabel(marks(KidsVerdict.UNRATED, kidsMark = 6)))
        assertEquals("From 12", kidsLabel(marks(KidsVerdict.UNRATED, kidsMark = 12)))
        assertEquals(listOf(null to "Not for kids", 6 to "From 6", 12 to "From 12"), KIDS_CHOICES.map { it.age to it.label })
    }
}
