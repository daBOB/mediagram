package player

import model.KIDS_LIMITS
import model.KidsVerdict

/**
 * What the Kids control says — the web player's own (`refreshKids` in
 * `player-library-marks.js`): a rated title's verdict, locked, naming whether
 * it is for every kid or only one at 12; an unrated title's current choice,
 * the option its select shows.
 */
fun kidsLabel(marks: PlayerMarksState): String =
    when (marks.kidsVerdict) {
        KidsVerdict.SAFE -> if (marks.forEveryKid) "For kids · ${marks.ageLabel}" else "For kids from ${KIDS_LIMITS.max()} · ${marks.ageLabel}"
        KidsVerdict.UNSAFE -> "${marks.ageLabel} · not for kids"
        KidsVerdict.UNRATED -> (KIDS_CHOICES.firstOrNull { it.age == marks.kidsMark } ?: KIDS_CHOICES.first()).label
    }

/** One answer the Kids control offers on an unrated title — an option of the web's select. */
data class KidsChoice(
    val age: Int?,
    val label: String,
)

/** "Not for kids", then a mark from each limit a kid can have. */
val KIDS_CHOICES: List<KidsChoice> = listOf(KidsChoice(null, "Not for kids")) + KIDS_LIMITS.map { KidsChoice(it, "From $it") }
