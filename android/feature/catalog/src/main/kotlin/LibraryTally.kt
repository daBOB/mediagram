package catalog

/*
 * The Settings index's tablet-only tally, under the four rows — the web
 * rail masthead's own three lines (`app.js:121-123`), spelled the way
 * `format.js`'s `spellCount`/`countOf` do: a count small enough to read
 * faster as a word than as a figure is one, everything past twenty is
 * figures. `ui.catalog.countOf` (ui-common) is figures-only on purpose —
 * a search result count is never small enough for the distinction to
 * matter — so this is its own small port rather than a shared function.
 */

private val NUMBER_WORDS =
    listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty",
    )

/** A count as a word while it is small enough to be one, else as figures. Mirrors the web's `spellCount`. */
private fun spellCount(count: Int): String = if (count in 0..20) NUMBER_WORDS[count] else count.toString()

/**
 * `three shows`, `one show`, `170 lessons` — mirrors the web's `countOf`,
 * spelled rather than figures-only. Public: the magazine home page's own
 * captions (a series' episode/season count, a course's lesson/chapter
 * count) spell the same way, on both Android surfaces — `ui.catalog.home.countOf`
 * (ui-mobile) is a one-line delegate to this rather than a second copy of
 * [spellCount]'s own word list.
 */
fun spelledCountOf(
    count: Int,
    noun: String,
): String {
    if (count == 1) return "${spellCount(count)} $noun"
    val plural = if (Regex("[^aeiou]y$", RegexOption.IGNORE_CASE).containsMatchIn(noun)) "${noun.dropLast(1)}ies" else "${noun}s"
    return "${spellCount(count)} $plural"
}

/** The noun each of [shelvesOf]'s own shelves counts in — the web's `SECTIONS[…].extent` (`sections.js`). */
private val EXTENT_NOUNS = mapOf("Movies" to "film", "Series" to "show", "Tutorials" to "course")

/**
 * Settings index's tablet-only tally lines: the library's own shelves,
 * spelled the way the web rail's masthead is — "911 films" / "43 shows" /
 * "four courses" (`round2/b-telegram.html`'s `.masthead`).
 */
fun libraryTallyLines(shelves: List<Shelf>): List<String> =
    shelves.mapNotNull { shelf -> EXTENT_NOUNS[shelf.title]?.let { noun -> spelledCountOf(shelf.entries.size, noun) } }
