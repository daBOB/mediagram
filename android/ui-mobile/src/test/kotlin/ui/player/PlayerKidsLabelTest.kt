package ui.player

import model.KidsVerdict
import player.PlayerMarksState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three wordings `refreshKids` in `player.js` gives the Kids button. */
class PlayerKidsLabelTest {
    private fun marks(
        verdict: KidsVerdict,
        label: String? = null,
        marked: Boolean = false,
    ) = PlayerMarksState(
        watchlisted = false,
        kids = marked,
        lists = emptyList(),
        memberOf = emptySet(),
        kidsVerdict = verdict,
        ageLabel = label,
    )

    @Test
    fun aRatedTitleSaysWhatItsRatingDecided() {
        assertEquals("For kids · FSK 6", kidsLabel(marks(KidsVerdict.SAFE, "FSK 6")))
        assertEquals("FSK 16 · not for kids", kidsLabel(marks(KidsVerdict.UNSAFE, "FSK 16")))
    }

    @Test
    fun anUnratedTitleSaysWhetherItIsMarked() {
        assertEquals("Kids", kidsLabel(marks(KidsVerdict.UNRATED)))
        assertEquals("For kids", kidsLabel(marks(KidsVerdict.UNRATED, marked = true)))
    }
}
