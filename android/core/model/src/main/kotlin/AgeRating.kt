package model

/**
 * Age ratings, and what they decide about the Kids shelf — a port of the web
 * player's `age-rating.js`, whose rules the household chose:
 *
 *  - Rated at or below [KIDS_AGE_LIMIT]: for kids by itself. Nobody has to
 *    mark it, and a mark cannot take it off — the rating decides.
 *  - Rated above it: never for kids. Marking it is refused, and a mark made
 *    before ratings were recorded no longer counts.
 *  - Unrated: only what someone marked by hand.
 */
const val KIDS_AGE_LIMIT = 12

/** Which of the three rules applies to a title. */
enum class KidsVerdict { SAFE, UNSAFE, UNRATED }

/**
 * The rating as an age, or null when there is none that reads as one. FSK is
 * always a bare number; a letter rating from another country is not an age
 * this rule can compare, so it counts as unrated.
 */
fun ageOf(fsk: String?): Int? = fsk?.trim()?.takeIf { AGE.matches(it) }?.toInt()

/** `"FSK 12"`, or null for an unrated title. */
fun ageLabelOf(fsk: String?): String? = ageOf(fsk)?.let { "FSK $it" }

fun kidsVerdictOf(fsk: String?): KidsVerdict {
    val age = ageOf(fsk) ?: return KidsVerdict.UNRATED
    return if (age <= KIDS_AGE_LIMIT) KidsVerdict.SAFE else KidsVerdict.UNSAFE
}

fun MediaSet.ageLabel(): String? = ageLabelOf(fsk)

fun MediaSet.kidsVerdict(): KidsVerdict = kidsVerdictOf(fsk)

private val AGE = Regex("""\d{1,2}""")
