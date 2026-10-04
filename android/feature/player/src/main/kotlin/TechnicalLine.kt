package player

import model.MediaSet
import model.bitrateLabel
import model.hdrLabel
import model.humanSize

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
fun technicalLine(set: MediaSet): String =
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
 * `5 parts`, or nothing for a set that is a single message.
 *
 * A one-part set is the ordinary case and saying so is noise; a set split
 * into several is the reason a download can stall halfway through one.
 */
private fun partsLabel(count: Int): String? = if (count > 1) "$count parts" else null
