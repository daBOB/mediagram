package catalog

/**
 * A department of the library, and the words its counts are spelled in —
 * the web player's `SECTIONS` (`sections.js`), declared in the order
 * [shelvesOf] builds its shelves. [label] is what the masthead calls it;
 * [extent] is what its own count line counts in ("581 films"); [noun] is
 * what one of its shows or courses counts its items in, and Movies, a
 * department of single films, has none.
 *
 * A shelf, a tab and a hero are told apart by this rather than by the label
 * they print, so renaming a label can never send a tab to the wrong page.
 */
enum class Department(
    val label: String,
    val extent: String,
    val noun: String?,
) {
    MOVIES("Movies", "film", null),
    SERIES("Series", "show", "episode"),
    ANIME("Anime", "title", "episode"),
    DOCUMENTARIES("Documentaries", "documentary", "documentary"),
    TUTORIALS("Tutorials", "course", "lesson"),
}
