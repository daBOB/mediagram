package ui

import java.util.Locale
import model.MediaSet

/**
 * What a file actually is, in one line: `1080p · HDR10 · mkv · hevc · eac3
 * · 14 GB · 5 parts · 9.4 Mbps`. Mirrors `technicalLine` in the web
 * player's `format.js`, down to the field order, the omissions, and the
 * casing — container and codecs are printed as the index stored them, not
 * uppercased, because a surface that shows `MKV` while the other shows
 * `mkv` has given a viewer two ways to describe one file. Wherever a
 * surface wants the shout-case form, it upper-cases at render time, the way
 * `course-view.js` does — this line stays as stored.
 */
internal fun technicalLine(set: MediaSet): String =
    listOfNotNull(
        set.quality,
        hdrLabel(set.hdr),
        set.container.takeIf { it.isNotEmpty() },
        set.vcodec?.takeIf { it.isNotEmpty() },
        set.acodec?.takeIf { it.isNotEmpty() },
        // humanSize(0) is "0 B", a fact about nothing; a set that somehow
        // has no size should stay quiet rather than print it.
        set.totalBytes.takeIf { it > 0 }?.let(::humanSize),
        partsLabel(set.partCount),
        bitrateLabel(set.totalBytes, set.durationSecs),
    ).joinToString(" · ")

/**
 * What a title's dynamic range is worth saying, or nothing.
 *
 * `SDR` is left out on purpose: it is the absence of a fact rather than a
 * fact, and a shelf where every card says `SDR` says nothing at all.
 */
internal fun hdrLabel(hdr: String?): String? = hdr?.takeIf { it.isNotEmpty() && it != "SDR" }

/**
 * The average bitrate of a set, as `9.4 Mbps`.
 *
 * `totalBytes / durationSeconds` over the whole file, which is what a link
 * has to carry on average rather than what any one second peaks at. Null
 * unless both numbers are known and positive: either missing would
 * otherwise read as a fabricated rate.
 */
internal fun bitrateLabel(totalBytes: Long, durationSeconds: Int?): String? {
    if (durationSeconds == null || durationSeconds <= 0 || totalBytes <= 0) return null
    val mbps = totalBytes * 8.0 / durationSeconds / 1_000_000.0
    // Under 10 Mbit/s the first decimal is the difference between a link
    // that carries it and one that does not; above, it is a digit nobody
    // reads.
    val rounded = if (mbps < 10) {
        String.format(Locale.ROOT, "%.1f", mbps)
    } else {
        Math.round(mbps).toString()
    }
    return "$rounded Mbps"
}

/**
 * `5 parts`, or nothing for a set that is a single message.
 *
 * A one-part set is the ordinary case and saying so is noise; a set split
 * into several is the reason a download can stall halfway through one.
 */
private fun partsLabel(count: Int): String? = if (count > 1) "$count parts" else null
