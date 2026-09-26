package model

/**
 * A single item in the library: a movie, one episode of a show, a lesson
 * of a course, or a document belonging to one. Carries a flattened
 * [episodeFirst]/[episodeLast] pair rather than a separate episode object,
 * since one set can span more than one episode.
 */
data class MediaSet(
    val setId: String,
    val kind: Kind,
    val title: String,
    val show: String?,
    /** The chapter title a course names this set's folder by, if it has one. */
    val chapter: String?,
    /**
     * Where the set sat inside its collection, as `a/b/c` from the top down.
     *
     * A course nests unevenly — one folder deep in places and four in
     * others — so only this describes its shape. [chapter] is one flattened
     * rendering of the same trail and cannot be split back into it.
     */
    val path: String?,
    val season: Int?,
    val episodeFirst: Int?,
    val episodeLast: Int?,
    val year: Int?,
    val durationSecs: Int?,
    val posterPath: String?,
    val totalBytes: Long,
    /** As stored, e.g. `mkv`; empty when the index recorded none. */
    val container: String = "",
    val vcodec: String? = null,
    val acodec: String? = null,
    /** e.g. `1080p`; null when the index did not record a resolution. */
    val quality: String? = null,
    /** e.g. `HDR10`, `HLG`, `DV`, or `SDR` for the ordinary case. */
    val hdr: String? = null,
    /** How many messages this set is split across; 1 for the ordinary case. */
    val partCount: Int = 1,
    /** The raw key a title-detail screen asks the core to resolve a poster for. */
    val posterKey: String? = null,
    /**
     * When this set arrived, as a Unix time, or 0 from an index that did
     * not record one. It is what "latest" means here: a library is added to
     * over years, and what turned up last week is the question a start page
     * exists to answer.
     */
    val addedAt: Long = 0,
    /**
     * The age rating in the library's country (`"12"`), or null when the
     * title has none. An episode carries its show's. Named as the web
     * player names it; what it decides is in `AgeRating.kt`.
     */
    val fsk: String? = null,
    /** The provider's genres for this title. An episode carries its show's. */
    val genres: List<String> = emptyList(),
    /** Languages this set has a subtitle track for. */
    val subtitleLanguages: List<String> = emptyList(),
    /** Whether the index holds a plot summary for this set. */
    val hasSummary: Boolean = false,
    /**
     * What the index actually named this set, before [title]'s own fallback
     * to [show] or [setId] filled in for a title that was never given one.
     * [title] is right for anything that needs *some* string to show; this
     * is for the one thing that needs to tell "titled" apart from "not" —
     * the player's title line, which drops a segment rather than repeat
     * the show's name or print a raw set id back at the viewer.
     */
    val rawTitle: String? = null,
    /**
     * The resolved path to this title's backdrop, present only when the
     * file actually exists on disk — see `store::list_sets` on the Rust
     * side. An episode carries its show's, like [genres].
     */
    val backdropPath: String? = null,
    /** The provider's tagline, for the home page's typographic break. An episode carries its show's. */
    val tagline: String? = null,
    /** The provider's average rating, for the staff pick. An episode carries its show's. */
    val rating: Double? = null,
    /** The provider's popularity figure, for the trending feature. An episode carries its show's. */
    val popularity: Double? = null,
    /**
     * TMDB's `belongs_to_collection` id for a film, or `null` for a title
     * with none. An episode carries `null`: a show is not a TMDB collection.
     */
    val collectionId: Long? = null,
    /** The collection's own name, alongside [collectionId]; `null` when it is. */
    val collectionName: String? = null,
    /** TMDB's kind for a show, e.g. `"scripted"`, `"documentary"`; `null` when unknown. */
    val seriesType: String? = null,
    /** TMDB's status for a show, e.g. `"Ended"`, `"Returning Series"`; `null` when unknown. */
    val showStatus: String? = null,
)

enum class Kind {
    MOVIE,
    EPISODE,
    TUTORIAL,

    /**
     * A handout beside a lesson, or a workbook in a folder holding no video
     * at all. It rides with the course it belongs to and is not playable:
     * the index says what it is, and no container sniffing is involved.
     */
    DOCUMENT,
}
