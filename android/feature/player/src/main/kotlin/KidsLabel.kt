package player

import model.KidsVerdict

/** What the Kids button says — the three wordings `refreshKids` in `player.js` chooses between. */
fun kidsLabel(marks: PlayerMarksState): String =
    when (marks.kidsVerdict) {
        KidsVerdict.SAFE -> "For kids · ${marks.ageLabel}"
        KidsVerdict.UNSAFE -> "${marks.ageLabel} · not for kids"
        KidsVerdict.UNRATED -> if (marks.kids) "For kids" else "Kids"
    }
