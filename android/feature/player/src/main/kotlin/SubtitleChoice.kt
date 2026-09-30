package player

import model.SubtitleTrackInfo
import playback.audioLanguageLabel

/** The value a chosen-off subtitle is remembered as — a choice like any other, never "nothing remembered". */
const val SUBTITLES_OFF: String = data.SUBTITLE_OFF

/** One row the Subtitles section offers: "Off", or a track marked selected against whichever is shown. */
data class SubtitleOption(val value: String, val label: String, val selected: Boolean)

/** What [chooseSubtitles] resolved: a regular track's key, and/or a forced track's language — see `plan.md`'s playback rule, which this ports line for line. */
data class SubtitleSelection(val regular: String?, val forced: String?)

/** Whether the CC control and the style/offset controls have anything to act on. */
data class SubtitleVisibility(val ccVisible: Boolean, val styleVisible: Boolean)

/** ger/deu → de, eng → en — the only bibliographic/terminology split this library's uploader ever produces; anything else passes through lower-cased. */
private fun normalizeLang(tag: String): String = when (tag.lowercase()) {
    "ger", "deu" -> "de"
    "eng" -> "en"
    else -> tag.lowercase()
}

/** `audio = playing stream's language tag ?? first of the set's alang ?? unknown`. */
fun audioLanguage(playingTag: String?, alang: List<String>): String? = (playingTag ?: alang.firstOrNull())?.let(::normalizeLang)

/** A regular (non-forced) track's key: its language, with `:sdh` appended for the SDH variant of it — how a remembered/preferred choice and a picker row are both keyed. */
fun trackKey(track: SubtitleTrackInfo): String = if (track.sdh) "${track.lang}:sdh" else track.lang

/** [candidate] resolved against [regular]: the exact key, else the same language's plain track, else its SDH one — `null` for nothing regular in that language at all. */
private fun resolveCandidate(candidate: String?, regular: List<SubtitleTrackInfo>): String? {
    if (candidate == null) return null
    regular.firstOrNull { trackKey(it) == candidate }?.let { return trackKey(it) }
    val base = candidate.substringBefore(':')
    regular.firstOrNull { it.lang == base && !it.sdh }?.let { return trackKey(it) }
    regular.firstOrNull { it.lang == base && it.sdh }?.let { return trackKey(it) }
    return null
}

/**
 * The regular track key to show and the forced language beside — or instead
 * of — it. Ports `plan.md`'s rule exactly:
 * `regular = wanted off ? none : same key → same lang plain → same lang SDH → profile preference → none`;
 * `forced = no regular showing && audio known ? forced track in the audio language : none` — shown even when subtitles are off.
 */
fun chooseSubtitles(remembered: String?, preferred: String?, audio: String?, tracks: List<SubtitleTrackInfo>): SubtitleSelection {
    val regular = tracks.filterNot { it.forced }
    val wanted = remembered ?: preferred ?: SUBTITLES_OFF
    val chosen = if (wanted == SUBTITLES_OFF) {
        null
    } else {
        // wanted is remembered's own value when it is set, else preferred's
        // — either way the first candidate tried; remembered gets a second
        // try against preferred when it fails, since it was chosen over it.
        resolveCandidate(wanted, regular) ?: remembered?.let { resolveCandidate(preferred, regular) }
    }
    val forced = if (chosen == null && audio != null) tracks.firstOrNull { it.forced && it.lang == audio }?.lang else null
    return SubtitleSelection(chosen, forced)
}

/**
 * Which track key a viewer turning subtitles on lands on:
 * `last regular this session ?? profile preference (unless off) ?? regular in the audio language ?? first regular`.
 * `null` for a file with no regular track to turn on at all.
 */
fun toggleOn(last: String?, preferred: String?, audio: String?, tracks: List<SubtitleTrackInfo>): String? {
    val regular = tracks.filterNot { it.forced }
    if (regular.isEmpty()) return null
    resolveCandidate(last, regular)?.let { return it }
    if (preferred != null && preferred != SUBTITLES_OFF) resolveCandidate(preferred, regular)?.let { return it }
    if (audio != null) resolveCandidate(audio, regular)?.let { return it }
    return trackKey(regular.first())
}

/** Off plus every regular track, in file order — empty when the file offers no regular track, which is also what hides the section entirely. */
fun subtitleOptions(tracks: List<SubtitleTrackInfo>, shown: String?): List<SubtitleOption> {
    val regular = tracks.filterNot { it.forced }
    if (regular.isEmpty()) return emptyList()
    val options = mutableListOf(SubtitleOption(SUBTITLES_OFF, "Off", shown == null))
    for (track in regular) {
        val key = trackKey(track)
        options += SubtitleOption(key, subtitleTrackLabel(track), shown == key)
    }
    return options
}

fun visibility(tracks: List<SubtitleTrackInfo>): SubtitleVisibility =
    SubtitleVisibility(ccVisible = tracks.any { !it.forced }, styleVisible = tracks.isNotEmpty())

/** The uploader's own label when it wrote one, else a language name — same naming policy as the audio menu's label. */
private fun subtitleTrackLabel(track: SubtitleTrackInfo): String =
    track.label.trim().ifEmpty { audioLanguageLabel(track.lang, "Subtitles") }
