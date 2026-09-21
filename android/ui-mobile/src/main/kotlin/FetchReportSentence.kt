package ui

/**
 * The sentence a fetch is reported with. Pure and `internal` — no composable
 * belongs here, only the wording.
 *
 * Six counts, and a viewer reading the line wants two things from it: did
 * this run find anything, and is anything still missing. So it leads with
 * what changed, follows with what was already there, and names the leftovers
 * only when there are any. A count of zero is left out rather than printed,
 * because "0 described" is a number nobody asked for.
 *
 * Every count is titles. A course of 162 lessons is one title with no
 * provider entry, not 162 of them — the phrasing can say "title" plainly
 * because the core already decided that is what it counts.
 */
@Suppress("LongParameterList")
internal fun fetchSentence(
    detailsRecorded: Int,
    postersFetched: Int,
    detailsAlreadyKnown: Int,
    postersAlreadyHeld: Int,
    noProviderId: Int,
    failed: Int,
): String {
    val found = listOfNotNull(
        phrase(detailsRecorded, "described"),
        phrase(postersFetched, "poster fetched", "posters fetched"),
    )
    val alreadyThere = listOfNotNull(
        phrase(detailsAlreadyKnown, "already described"),
        phrase(postersAlreadyHeld, "poster already held", "posters already held"),
    )
    val leftovers = listOfNotNull(
        phrase(noProviderId, "title has no provider entry", "titles have no provider entry"),
        phrase(failed, "could not be fetched"),
    )

    val sentences = mutableListOf<String>()
    when {
        found.isNotEmpty() -> {
            sentences += "${found.joinToString(", ")}."
            if (alreadyThere.isNotEmpty()) sentences += "${alreadyThere.joinToString(", ")}."
        }
        // The ordinary second run: nothing was wrong, there was simply
        // nothing left to ask for, and saying so in one clause keeps it from
        // reading like a run that failed.
        alreadyThere.isNotEmpty() -> sentences += "Nothing new — ${alreadyThere.joinToString(", ")}."
        leftovers.isEmpty() -> return "Nothing to fetch."
        else -> sentences += "Nothing new."
    }
    leftovers.forEach { sentences += "$it." }
    return sentences.joinToString(" ")
}

/**
 * "$count $noun", or nothing at all when the count is zero — a half of the
 * run that did nothing is not worth a clause. `plural` defaults to `singular`
 * for the phrases that do not inflect, so only the ones that do say so.
 */
private fun phrase(count: Int, singular: String, plural: String = singular): String? = when (count) {
    0 -> null
    1 -> "1 $singular"
    else -> "$count $plural"
}
