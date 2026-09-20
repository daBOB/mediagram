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
    val season: Int?,
    val episodeFirst: Int?,
    val episodeLast: Int?,
    val year: Int?,
    val durationSecs: Int?,
    val posterPath: String?,
    val totalBytes: Long,
)

enum class Kind { MOVIE, EPISODE, TUTORIAL }
