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
