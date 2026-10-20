package model

/** One of the library's five departments as a profile's access is spelled: its stored [key] and the [label] the control prints. */
data class Section(
    val key: String,
    val label: String,
)

/** In nav order, with the labels of the web player's `SECTIONS` (`sections.js`). */
val SECTIONS: List<Section> =
    listOf(
        Section("movies", "Movies"),
        Section("series", "Series"),
        Section("anime", "Anime"),
        Section("documentaries", "Documentaries"),
        Section("tutorials", "Tutorials"),
    )

/** The [Section] key this set sits in; anime leaves its kind's department first, as the catalog shelves it. */
fun MediaSet.sectionKey(): String =
    when {
        anime -> "anime"
        kind == Kind.DOCUMENTARY -> "documentaries"
        kind == Kind.EPISODE -> "series"
        kind == Kind.TUTORIAL || kind == Kind.DOCUMENT -> "tutorials"
        else -> "movies"
    }

/**
 * The keys this profile does not see. The owner hides nothing, whatever their
 * stored list says — a kid wrongly said to be the owner is still a kid; a
 * household's admin sees what the owner left them.
 */
val Profile.hiddenFrom: Set<String> get() = if (owner && !kids) emptySet() else hiddenSections.toSet()
