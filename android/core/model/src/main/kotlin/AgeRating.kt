package model

/** The limits a kids profile can have — FSK 6 or FSK 12, chosen by the grown-up it belongs to. */
val KIDS_LIMITS = listOf(6, 12)

/**
 * Which of the three rules applies to a title, at a kid's own limit `N`
 * ([Profile.kidsLimit]) — a port of the web player's `age-rating.js`, whose
 * rules the household chose:
 *
 *  - [SAFE], rated `N` or under: for that kid by itself. Nobody has to mark
 *    it, and a mark cannot take it off — the rating decides.
 *  - [UNSAFE], rated above `N`: not for that kid. A hand mark does not bring
 *    it back.
 *  - [UNRATED]: only what someone marked by hand, and only from an age at or
 *    under `N` — "from 6" is for every kid, "from 12" for a 12 only.
 */
enum class KidsVerdict { SAFE, UNSAFE, UNRATED }

/**
 * The rating as an age, or null when there is none that reads as one. FSK is
 * always a bare number; a letter rating from another country is not an age
 * this rule can compare, so it counts as unrated.
 */
fun ageOf(fsk: String?): Int? = fsk?.trim()?.takeIf { AGE.matches(it) }?.toInt()

/** `"FSK 12"`, or null for an unrated title. */
fun ageLabelOf(fsk: String?): String? = ageOf(fsk)?.let { "FSK $it" }

fun kidsVerdictOf(
    fsk: String?,
    limit: Int,
): KidsVerdict {
    val age = ageOf(fsk) ?: return KidsVerdict.UNRATED
    return if (age <= limit) KidsVerdict.SAFE else KidsVerdict.UNSAFE
}

fun MediaSet.ageLabel(): String? = ageLabelOf(fsk)

fun MediaSet.kidsVerdict(limit: Int): KidsVerdict = kidsVerdictOf(fsk, limit)

private val AGE = Regex("""\d{1,2}""")

/**
 * What a kid with [limit] sees — the web player's `forKidsProfile(sets,
 * marks, limit)`: rated at or under the limit, or unrated and marked from an
 * age at or under it. [marks] maps a set id to the age its mark is for kids
 * from. Applied once to the whole catalog, so every shelf agrees.
 */
fun forKidsProfile(
    sets: List<MediaSet>,
    marks: Map<String, Int>,
    limit: Int,
): List<MediaSet> =
    sets.filter {
        when (it.kidsVerdict(limit)) {
            KidsVerdict.SAFE -> true
            KidsVerdict.UNSAFE -> false
            KidsVerdict.UNRATED -> marks[it.setId]?.let { from -> from <= limit } == true
        }
    }
