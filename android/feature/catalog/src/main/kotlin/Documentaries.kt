package catalog

import model.MediaSet

/**
 * Documentaries split the way Tutorials groups, and then split further.
 *
 * A folder of documentaries groups by show/collection exactly as a course
 * does, so [collections] builds it unchanged. A documentary uploaded on its
 * own has no show name, and giving it one with a shared fallback would file
 * every single documentary in the library under one fake folder nobody
 * asked for; it stays a plain set instead, for the department page to show
 * in its own row — a pure Kotlin port of the web's `documentaries.js`.
 */
data class DocumentaryLibrary(
    val collections: List<Entry.Collection>,
    val singles: List<MediaSet>,
)

fun groupDocumentaries(sets: List<MediaSet>): DocumentaryLibrary {
    val grouped = sets.filter { !it.show.isNullOrBlank() }
    val singles = sets.filter { it.show.isNullOrBlank() }
    // Keyed under its own prefix rather than `collections()`'s own
    // "COURSE/<name>" — a course and a documentary folder are free to share
    // a name, and `CatalogUiState.collection(key)` resolves a key across
    // every shelf, so two different collections answering to the same key
    // would mean whichever shelf is searched first wins the tap.
    val documentaryCollections = collections(grouped, CollectionKind.COURSE, fallback = "")
        .map { it.copy(key = "DOCUMENTARY/${it.name}") }
    return DocumentaryLibrary(
        collections = documentaryCollections,
        singles = singles.sortedWith(compareBy(NATURAL) { it.title }),
    )
}

/**
 * How many documentaries [entries] holds, folders and singles alike — the
 * pill bar's own count. A folder is one card on the shelf but many
 * documentaries; [Entry.Collection.count] already carries how many.
 */
fun documentaryCountOf(entries: List<Entry>): Int =
    entries.sumOf { entry -> if (entry is Entry.Collection) entry.count else 1 }
