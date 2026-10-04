package model

import java.util.Locale

/*
 * Labels for what a file is, shared by the player's one-line technical
 * summary and a film page's Details fact sheet — kept beside [humanSize]
 * so both can reach them without one feature module depending on another.
 */

/**
 * What a title's dynamic range is worth saying, or nothing.
 *
 * `SDR` is left out on purpose: it is the absence of a fact rather than a
 * fact, and a shelf where every card says `SDR` says nothing at all.
 */
fun hdrLabel(hdr: String?): String? = hdr?.takeIf { it.isNotEmpty() && it != "SDR" }

/**
 * The average bitrate of a set, as `9.4 Mbps`.
 *
 * `totalBytes / durationSeconds` over the whole file, which is what a link
 * has to carry on average rather than what any one second peaks at. Null
 * unless both numbers are known and positive: either missing would
 * otherwise read as a fabricated rate.
 */
fun bitrateLabel(
    totalBytes: Long,
    durationSeconds: Int?,
): String? {
    if (durationSeconds == null || durationSeconds <= 0 || totalBytes <= 0) return null
    val mbps = totalBytes * 8.0 / durationSeconds / 1_000_000.0
    // Under 10 Mbit/s the first decimal is the difference between a link
    // that carries it and one that does not; above, it is a digit nobody
    // reads.
    val rounded =
        if (mbps < 10) {
            String.format(Locale.ROOT, "%.1f", mbps)
        } else {
            Math.round(mbps).toString()
        }
    return "$rounded Mbps"
}
