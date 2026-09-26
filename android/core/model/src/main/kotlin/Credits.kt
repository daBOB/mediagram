package model

/**
 * One person credited on a title, or found by a name search: their id, name,
 * the character they played (cast) or their job (crew), and their portrait's
 * resolved file — present only when this device already holds it. Mirrors
 * the core's `CreditRecord`.
 */
data class Credit(
    val personId: Long,
    val name: String,
    val role: String?,
    val portraitPath: String?,
)

/** A title's cast, in billing order, apart from its crew (director(s), a series' creators). */
data class TitleCredits(
    val cast: List<Credit>,
    val crew: List<Credit>,
) {
    companion object {
        /** What an index with no `credits` table (v8 and older) answers — not an error. */
        val Empty = TitleCredits(emptyList(), emptyList())
    }
}

/**
 * One person and the keys of every title they are credited on — resolved
 * against the rows a profile was already allowed to see, so a Kids profile
 * is shown only the titles it can already open. Mirrors the core's
 * `PersonRecord`.
 */
data class Person(
    val personId: Long,
    val name: String,
    val portraitPath: String?,
    val titleKeys: List<String>,
)

/** A film franchise (TMDB "collection"), by name — mirrors the core's `FranchiseRecord`. */
data class FranchiseInfo(
    val id: Long,
    val name: String,
    val overview: String?,
)

/** One name a people search found, most-credited people surfacing first. Mirrors the core's `PeopleHitRecord`. */
data class PersonHit(
    val personId: Long,
    val name: String,
    val portraitPath: String?,
    val titleKeys: List<String>,
)
