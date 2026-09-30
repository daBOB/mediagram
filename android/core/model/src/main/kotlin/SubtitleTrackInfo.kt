package model

/**
 * One subtitle track a [MediaSet] offers — a position in its bundle, once
 * uploaded, or a position among its inline rows until then. Mirrors the
 * core's `SubtitleTrack` record.
 */
data class SubtitleTrackInfo(
    /** This track's position — what `CoreInterface.subtitleText` is asked for. */
    val track: Int,
    val lang: String,
    val forced: Boolean,
    val sdh: Boolean,
    val label: String,
)
