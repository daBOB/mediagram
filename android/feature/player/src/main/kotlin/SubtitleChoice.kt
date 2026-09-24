package player

import playback.audioLanguageLabel

/** The value a chosen-off subtitle is remembered as — a choice like any other, never "nothing remembered". */
const val SUBTITLES_OFF: String = "off"

/** One row the Subtitles section offers: "Off", or a language, marked selected against whichever is chosen. */
data class SubtitleOption(val value: String, val label: String, val selected: Boolean)

/**
 * Which subtitle language a title just opened comes up on — a port of the
 * web's default rule (`transport.js`'s `offerSubtitles`): a remembered
 * "off" is a choice like any other and is respected; a remembered language
 * this file still carries is restored; anything else — nothing remembered,
 * or a language this file has lost — falls back to the first language this
 * file carries, or "off" for a file with none.
 */
fun chooseSubtitleLanguage(available: List<String>, remembered: String?): String {
    if (remembered == SUBTITLES_OFF) return SUBTITLES_OFF
    val match = available.firstOrNull { it.equals(remembered, ignoreCase = true) }
    if (match != null) return match
    return available.firstOrNull() ?: SUBTITLES_OFF
}

/**
 * The rows to offer for [available], [chosen] marked selected. Empty for a
 * file with no subtitles at all — "Off" alone is not a choice, the same rule
 * the web's own picker uses (`subs.hidden = options.length < 2`) — which is
 * also what the sheet reads to decide whether to show the section at all.
 */
fun subtitleOptions(available: List<String>, chosen: String): List<SubtitleOption> {
    if (available.isEmpty()) return emptyList()
    val options = mutableListOf(SubtitleOption(SUBTITLES_OFF, "Off", chosen == SUBTITLES_OFF))
    for (language in available) {
        options += SubtitleOption(language, subtitleLanguageLabel(language), chosen == language)
    }
    return options
}

/** English display name for a language tag — same naming policy as the audio menu's own label, "Subtitles" rather than a track number for the untagged case, since a subtitle track has no ordinal a viewer would recognise. */
private fun subtitleLanguageLabel(language: String): String = audioLanguageLabel(language, "Subtitles")
