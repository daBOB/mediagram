package playback

import java.util.Locale

/**
 * The audio track menu: what the file holds, named for a person.
 *
 * [AudioTrackFacts] carries only plain fields read off a `Format` — never
 * the `Format`/`Tracks` themselves — so every rule below is testable on a
 * bare JVM the same way a menu row can be built from a plain object in
 * JavaScript: no `TrackGroup`, no `Tracks.Group`, no Android runtime to
 * stand in for. `groupIndex`/`trackIndex` are the two exceptions: they are
 * plain integers, not `Format` fields, kept here only so a picked
 * [AudioOption] carries what a real `TrackSelectionOverride` needs to be
 * built from, without this file ever constructing one. A track this
 * device cannot decode never reaches here at all — see
 * `AudioTrackSelection.extractAudioFacts`, which is also where
 * [isSelected] is read, off ExoPlayer's own live selection.
 */

/** One audio track's `Format` fields, reduced to what a label and a match need. */
data class AudioTrackFacts(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val channelCount: Int,
    val sampleMimeType: String?,
    /** Whether ExoPlayer's own selector is currently decoding this track — the fallback for a menu with nothing remembered; see [audioOptions]. */
    val isSelected: Boolean,
)

/** One row the audio sheet offers, and enough to build the override that selects it. */
data class AudioOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val text: String,
    val selected: Boolean,
)

/**
 * The name to show for [language], or [fallback] when it names nothing
 * useful. `und` is the standard's way of saying "undetermined", which is
 * exactly what a file with no language metadata deserves, and exactly what
 * a viewer should not be shown.
 *
 * Always in English, the same as the web's own `languageLabel`
 * (`Intl.DisplayNames(["en"], …)`) and this app's stats overlay
 * (`PlaybackStatRows.languageLabel`): every other string this app shows is
 * English, and a menu that alone followed the device's own locale would be
 * the inconsistent one, not the fix.
 */
fun audioLanguageLabel(language: String?, fallback: String): String {
    if (language.isNullOrBlank() || language.equals("und", ignoreCase = true)) return fallback
    val display = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
    // A tag Locale doesn't recognise names nothing, which Java reports as an
    // empty string rather than an exception — better an unfamiliar code on
    // the row than a confident wrong name.
    return display.ifBlank { language }
}

/**
 * MIME subtypes whose ffprobe name isn't just the lower-cased tail —
 * `codec_name`, exactly, since that is what the web's own chooser shows
 * (`audio-chooser.js` reads it straight off the server's probe). Profile
 * suffixes (`;profile=lbr`) are stripped before this is consulted, so a DTS
 * Express stream matches the same entry as plain DTS — ffprobe carries
 * that distinction in a separate `profile` field, not in `codec_name`.
 */
private val CODEC_NAMES = mapOf(
    "ac3" to "ac3",
    "eac3" to "eac3",
    "eac3-joc" to "eac3",
    "mp4a-latm" to "aac",
    "mpeg" to "mp3",
    "mpeg-L1" to "mp1",
    "mpeg-L2" to "mp2",
    "vnd.dts" to "dts",
    "vnd.dts.hd" to "dts",
    "true-hd" to "truehd",
    "raw" to "pcm",
)

/** A media3 audio MIME (`audio/vnd.dts.hd;profile=lbr`) said the way ffprobe names a codec (`dts`). Unmapped subtypes fall back to their own lower-cased name rather than a guess. */
private fun audioCodecName(sampleMimeType: String): String {
    val subtype = sampleMimeType.substringAfter('/').substringBefore(';')
    return CODEC_NAMES[subtype] ?: subtype.lowercase(Locale.ROOT)
}

/** One row's text: language (or a fallback), the stream's own title, channels, codec — whichever of those the file actually carries. */
fun audioTrackLabel(facts: AudioTrackFacts, ordinal: Int): String {
    val parts = mutableListOf(audioLanguageLabel(facts.language, "Track $ordinal"))
    facts.label?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
    channelLayoutLabel(facts.channelCount).takeIf { it.isNotEmpty() }?.let(parts::add)
    facts.sampleMimeType?.let { parts.add(audioCodecName(it)) }
    return parts.joinToString(" · ")
}

/**
 * The track carrying [language], or `null` when this file has none.
 *
 * A remembered choice is a **language**, never an ordinal: a track's
 * position in the file is not stable across a re-rip of the same show, so a
 * stored number would silently hand a viewer the wrong stream the first
 * time a file was replaced. A language tag either matches or it does not.
 */
fun audioTrackForLanguage(tracks: List<AudioTrackFacts>, language: String?): Int? {
    val wanted = language?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT) ?: return null
    val found = tracks.indexOfFirst { (it.language ?: "").lowercase(Locale.ROOT) == wanted }
    return found.takeIf { it != -1 }
}

/**
 * Whether [language] is worth remembering — never blank, and never `und`,
 * the tag for a stream that named none. The web only ever writes a real
 * language too (`player.js`'s change handler); an untagged or `und` pick
 * is honoured for the session it was made in but never saved, so it can't
 * blank out a good choice already on record for this show.
 */
fun isUsableAudioLanguage(language: String?): Boolean =
    !language.isNullOrBlank() && !language.equals("und", ignoreCase = true)

/**
 * The rows to offer for [tracks]. [rememberedLanguage] marks its track
 * selected when it names one of them; with nothing remembered (or a
 * language this file has lost), nothing here is pinned — the selected row
 * is whichever one ExoPlayer's own selector already picked
 * ([AudioTrackFacts.isSelected]), never a guess of this app's own. Empty
 * for zero or one track: a single stream is not a menu, it is a label for
 * something nobody can change.
 */
fun audioOptions(tracks: List<AudioTrackFacts>, rememberedLanguage: String?): List<AudioOption> {
    val remembered = audioTrackForLanguage(tracks, rememberedLanguage)
    return audioOptionsMarking(tracks) { ordinal, _ -> remembered?.let { it == ordinal } }
}

/**
 * The rows for [tracks] with ([groupIndex], [trackIndex]) marked selected —
 * used once the viewer has picked a row by hand, when it is that exact row
 * that lights up regardless of what language it carries (or does not):
 * [audioOptions]'s own language match can never find an untagged pick.
 */
fun audioOptionsSelecting(tracks: List<AudioTrackFacts>, groupIndex: Int, trackIndex: Int): List<AudioOption> =
    audioOptionsMarking(tracks) { _, facts -> facts.groupIndex == groupIndex && facts.trackIndex == trackIndex }

/** Shared shape for [audioOptions] and [audioOptionsSelecting]: build every row, and ask [selected] which one to mark — falling back to ExoPlayer's own live pick when it answers `null`, meaning "nothing decided this any other way". */
private inline fun audioOptionsMarking(
    tracks: List<AudioTrackFacts>,
    selected: (ordinal: Int, facts: AudioTrackFacts) -> Boolean?,
): List<AudioOption> {
    if (tracks.size <= 1) return emptyList()
    return tracks.mapIndexed { ordinal, facts ->
        AudioOption(
            groupIndex = facts.groupIndex,
            trackIndex = facts.trackIndex,
            language = facts.language,
            text = audioTrackLabel(facts, ordinal + 1),
            selected = selected(ordinal, facts) ?: facts.isSelected,
        )
    }
}
