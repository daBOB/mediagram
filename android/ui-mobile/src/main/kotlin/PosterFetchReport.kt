package ui

/**
 * The sentence a poster fetch is reported with. Pure and `internal` — no
 * composable belongs here, only the wording.
 *
 * Four counts, kept apart rather than folded into a single pass/fail
 * verdict: artwork already on disk was not fetched, a title with no
 * provider entry is not a failure, and a genuine failure is named last so
 * it reads as the exception rather than the summary.
 */
internal fun posterReportLine(fetched: Int, alreadyHeld: Int, noProviderId: Int, failed: Int): String {
    if (fetched == 0 && alreadyHeld == 0 && noProviderId == 0 && failed == 0) {
        return "No artwork to fetch."
    }

    val sentences = mutableListOf("Fetched $fetched ${countNoun(fetched, "poster", "posters")}.")
    if (alreadyHeld > 0) {
        sentences += "$alreadyHeld ${countNoun(alreadyHeld, "was", "were")} already held."
    }
    if (noProviderId > 0) {
        val noun = countNoun(noProviderId, "title", "titles")
        val verb = countNoun(noProviderId, "has", "have")
        sentences += "$noProviderId $noun $verb no provider entry."
    }
    if (failed > 0) {
        sentences += "$failed could not be fetched."
    }
    return sentences.joinToString(" ")
}

/** `singular` for exactly one, `plural` otherwise — "1 posters" is the kind of thing that makes a careful app look careless. */
private fun countNoun(count: Int, singular: String, plural: String): String = if (count == 1) singular else plural
