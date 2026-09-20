package model

/**
 * A single item in the library: a movie, one episode of a show, or a
 * tutorial video. Carries a flattened [episodeFirst]/[episodeLast] pair
 * rather than a separate episode object, since one set can span more than
 * one episode.
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
)

enum class Kind { MOVIE, EPISODE, TUTORIAL }
