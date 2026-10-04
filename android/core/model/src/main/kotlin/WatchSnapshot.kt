package model

/**
 * Where a profile is in one title. `at`/`duration` are seconds, never a
 * percentage — a set's runtime can be unknown, and a percentage recorded
 * against an unknown length cannot be turned back into a position to seek
 * to. Mirrors the core's `ProgressRow` so nothing above [data.WatchStateRepository]
 * depends on the generated bindings directly.
 */
data class Progress(
    val setId: String,
    val at: Double,
    val duration: Double?,
    val updatedAt: Long,
)

/** One title a profile watched to the end, and when. */
data class Watched(
    val setId: String,
    val finishedAt: Long,
)

/**
 * A named group of sets a profile collected — a collection's contents.
 * `id` is the core's own row id; renaming or deleting one by it is
 * deferred to the phase that first shows collections in the UI.
 */
data class ListOfSets(
    val id: String,
    val name: String,
    val items: List<String>,
)

/**
 * Who is watching. A name is the cross-device identity a sync round
 * matches on; `id` is this device's local shorthand for it and means
 * nothing on another device.
 */
data class Profile(
    val id: String,
    val name: String,
    /** Sees only what [kidsLimit] allows. */
    val kids: Boolean = false,
    /** A kid's own limit, 6 or 12; null on a grown-up. */
    val kidsAge: Int? = null,
    /** The grown-up who made this kid, by this device's id for them — [ownerOf] covers a parent no longer here. */
    val parentId: String? = null,
    /** The household's one admin: adds and removes grown-ups and resets their PINs. Never a kid. */
    val admin: Boolean = false,
    /** Whether a PIN is set. Never the PIN or its hash — the core hands out neither. */
    val hasPin: Boolean = false,
) {
    /**
     * The FSK this kid sees up to. Anything but 6 reads as 12, the limit
     * every kid had before each had its own — the web's `kidsLimitOf`.
     */
    val kidsLimit: Int get() = if (kidsAge == KIDS_LIMITS.min()) kidsAge else KIDS_LIMITS.max()
}

/**
 * One profile's everything, in one read — progress, what has been finished,
 * the watchlist, which sets are marked for kids, and the collections built
 * on top of the library. [kids] is shared across every profile on this
 * account rather than one profile's own list; the core keeps it that way
 * and this only carries it through unchanged.
 */
data class WatchSnapshot(
    val progress: List<Progress>,
    val watched: List<Watched>,
    val watchlist: List<String>,
    val kids: List<String>,
    val collections: List<ListOfSets>,
    /**
     * The household's editor's choice, the title the home page leads its
     * features with — or `null` for no pick. Shared across every profile,
     * like [kids].
     */
    val editorsChoice: String? = null,
    /** The Kids marks made "from 6" — a subset of [kids]; every other mark is "from 12". */
    val kidsFromSix: List<String> = emptyList(),
) {
    /** Each Kids mark by the age it is for kids from — the `marks` [forKidsProfile] reads. */
    val kidsMarks: Map<String, Int> get() = kids.associateWith { if (it in kidsFromSix) KIDS_LIMITS.min() else KIDS_LIMITS.max() }

    companion object {
        val Empty = WatchSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
    }
}
