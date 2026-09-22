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
