package catalog

/**
 * A person as search offers them, before parity narrows the list to titles
 * this profile can see. Trivially mappable from [model.PersonHit]: an id, a
 * name, a resolved portrait file (present only when this device already
 * holds it), and the title keys they are credited on.
 */
data class PersonCandidate(
    val personId: Long,
    val name: String,
    val portraitPath: String?,
    val titleKeys: List<String>,
)

/** A person kept for search, counted by the titles this profile can see them in. */
data class VisiblePerson(val personId: Long, val name: String, val portraitPath: String?, val titles: Int)

/**
 * Search's people, narrowed to those credited on a title this profile can
 * see, each counted by the titles it can see — ported from `visiblePeople`
 * in the web's `cast.js`.
 *
 * Someone with no title this profile can see is dropped entirely, so a kids
 * profile never learns who is in a film it cannot open.
 *
 * @param isVisible whether a title key resolves to something this profile's
 *   library holds (the web's `byKey(key).films.length + .shows.length > 0`)
 */
fun visiblePeople(
    people: List<PersonCandidate>,
    isVisible: (String) -> Boolean,
): List<VisiblePerson> =
    people
        .map { person -> VisiblePerson(person.personId, person.name, person.portraitPath, person.titleKeys.count(isVisible)) }
        .filter { it.titles > 0 }

/**
 * Whether a provider key resolves to a film or a show this profile's own
 * (already kids-filtered) shelves hold — ported from `titlesByKey` in the
 * web's `cast.js`, collapsed to the one question [visiblePeople] and search's
 * own collections matching ever ask of it: is anything there at all.
 */
fun titlesByKey(shelves: List<Shelf>): (String) -> Boolean {
    val entries = shelves.asSequence().flatMap { it.entries }
    val filmKeys = entries.filterIsInstance<Entry.Film>().mapNotNull { it.set.posterKey }.toHashSet()
    val showKeys = entries.filterIsInstance<Entry.Collection>().mapNotNull { firstItemOf(it.divisions)?.posterKey }.toHashSet()
    return { key -> key in filmKeys || key in showKeys }
}
