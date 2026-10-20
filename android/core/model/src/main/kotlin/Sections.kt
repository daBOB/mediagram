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

/** The words of the Sections switches and the Everyone line — the web's `SECTION_ACCESS_WORDS`. */
object SectionAccessWords {
    const val TITLE = "Sections"
    const val HIDES = "hides"

    /** The switches' group, named for whose they are — the web's `aria-label`. */
    fun forName(name: String): String = "$TITLE for $name"
}

/** What the Everyone line adds for this profile ("hides Anime, Tutorials", in nav order), or null when it is shown every section. */
val Profile.hidesWords: String?
    get() = SECTIONS.filter { it.key in hiddenFrom }.takeIf { it.isNotEmpty() }?.joinToString(", ", "${SectionAccessWords.HIDES} ") { it.label }

/** This hidden list with [key] turned: hidden if it was shown, shown again if it was hidden; keys this app does not know kept as they came. */
fun List<String>.turning(key: String): List<String> = if (key in this) filter { it != key } else this + key
