package ui

import java.util.Locale
import playback.PlaybackTotals

/**
 * What the overlay says, from what the player reports. These read the
 * decoder rather than the catalog, so a title whose index is wrong about its
 * codec shows the truth here.
 *
 * All pure, all `internal`, none composable — the overlay is where these
 * sentences meet the layout.
 */

/**
 * MIME names whose stripped form is not what the codec is called. Everything
 * else — `hevc`, `avc`, `eac3`, `ac3`, `opus`, `vorbis` — comes out right
 * from the fallback below, so this only carries the exceptions.
 */
private val CODEC_NAMES = mapOf(
    "mp4a-latm" to "AAC",
    "x-vnd.on2.vp9" to "VP9",
)

/** A channel count a viewer recognises. Anything else is not worth guessing at, so it is left out. */
private val CHANNEL_LABELS = mapOf(1 to "Mono", 2 to "Stereo", 6 to "5.1", 8 to "7.1")

/** A media3 MIME type (`video/hevc`) said the way a viewer names a codec. */
private fun codecLabel(mime: String): String {
    val stripped = mime.substringAfter('/')
    return CODEC_NAMES[stripped] ?: stripped.uppercase(Locale.ROOT)
}

/** A bitrate in bits per second, as `9.4 Mbps`. */
private fun mbpsLabel(bitrate: Int): String {
    val mbps = bitrate / 1_000_000.0
    val rounded = if (mbps < 10) String.format(Locale.ROOT, "%.1f", mbps) else Math.round(mbps).toString()
    return "$rounded Mbps"
}

/**
 * A language code said the way a viewer names a language, in English
 * regardless of the device's own locale: every other string this app shows
 * is English, and a language name that alone followed the device's locale
 * would be the inconsistent one, not the fix.
 */
private fun languageLabel(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH)

/**
 * What the video decoder is doing: `1920×800 HEVC 9.4 Mbps`. Null width,
 * height or codec means the player has not resolved a format yet, which is
 * said plainly rather than left to print a broken shape. A bitrate of -1 is
 * media3's way of saying unset, not a rate, so it is left out rather than
 * printed as a number nobody measured.
 */
internal fun videoStatLine(width: Int?, height: Int?, codec: String?, bitrate: Int?): String {
    if (width == null || height == null || codec == null) return "not yet known"
    val base = "${width}×${height} ${codecLabel(codec)}"
    return if (bitrate != null && bitrate > 0) "$base ${mbpsLabel(bitrate)}" else base
}

/** What the audio decoder is doing: `EAC3 5.1 German`. */
internal fun audioStatLine(codec: String, channels: Int, language: String): String =
    listOfNotNull(codecLabel(codec), CHANNEL_LABELS[channels], languageLabel(language)).joinToString(" ")

/**
 * How far the loaded data runs past the playhead: `1:23 ahead`.
 *
 * A duration and nothing else, because a duration is all there is to report.
 * ExoPlayer says how far ahead it has loaded but never how many bytes that
 * came to, and the byte volumes it can answer for — what has been read, and
 * how much of it came off the disk — are the reads and cache rows' business.
 *
 * [held] says "cached" instead, the same swap the web's own
 * `preloadReadout` makes: a title on disk in full has the same short
 * buffer either way, and printing it read as the cache not working.
 */
internal fun bufferStatLine(aheadMs: Long, held: Boolean = false): String =
    if (held) "cached" else "${clockTime(aheadMs)} ahead"

/** The share of what has been read that came from disk rather than Telegram. */
internal fun cacheStatLine(totals: PlaybackTotals): String {
    val total = totals.fromCacheBytes + totals.fromUpstreamBytes
    if (total == 0L) return "nothing read yet"
    val percent = Math.round(totals.fromCacheBytes * 100.0 / total)
    return "$percent% from disk"
}

/**
 * How many round trips to Telegram have been made since the process started,
 * and what they carried. Not this title's own cost: the counters behind it run
 * for the life of the process, deliberately, because "what has this app
 * fetched" and "what did this film cost" are different questions and only the
 * first is asked here.
 */
internal fun readsStatLine(totals: PlaybackTotals): String {
    val base = "${totals.fetches} fetches · ${humanSize(totals.fromUpstreamBytes)}"
    return if (totals.failedReads > 0) "$base · ${totals.failedReads} failed" else base
}

/**
 * Frames the renderer dropped, or nothing. Zero dropped frames is the
 * ordinary case, and a row that always reads `0 frames` is noise a viewer
 * learns to ignore — which is exactly when it stops being read the one time
 * it matters.
 */
internal fun droppedStatLine(dropped: Int): String? = if (dropped > 0) "$dropped frames" else null
