package catalog

/** A shelf card's stable key: a film's set id, or a collection's own key. */
fun keyOf(entry: Entry): String =
    when (entry) {
        is Entry.Film -> entry.set.setId
        is Entry.Collection -> entry.key
    }

/**
 * What the plate counts in. A catalogue says "12 episodes", not "12 items",
 * and a course is measured in the chapters a viewer will work through
 * rather than in its total number of videos.
 */
fun extentOf(collection: Entry.Collection): String =
    when (collection.kind) {
        CollectionKind.SHOW -> "${collection.count} ${plural(collection.count, "episode")}"
        CollectionKind.COURSE -> "${collection.chapters} ${plural(collection.chapters, "chapter")}"
    }

private fun plural(
    count: Int,
    word: String,
): String = if (count == 1) word else "${word}s"
