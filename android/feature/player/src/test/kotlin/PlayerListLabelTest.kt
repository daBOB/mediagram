package player

import model.KidsVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

/** The two wordings `refreshWatchlist` in `player-library-marks.js` gives the list button. */
class PlayerListLabelTest {
    private fun marks(listed: Boolean) =
        PlayerMarksState(
            watchlisted = listed,
            kidsMark = null,
            lists = emptyList(),
            memberOf = emptySet(),
            kidsVerdict = KidsVerdict.UNRATED,
            ageLabel = null,
        )

    @Test
    fun theButtonNamesMyListAndSaysWhenTheTitleIsOnIt() {
        assertEquals("My List", listLabel(marks(listed = false)))
        assertEquals("On My List", listLabel(marks(listed = true)))
    }
}
