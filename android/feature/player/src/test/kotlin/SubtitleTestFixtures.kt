package player

import model.MediaSet
import model.SubtitleTrackInfo
import playback.TimedCue

/** An episode keyed under `key:show-x` — the scope every subtitle test's remembered values sit under. */
internal val subtitledShow = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "30 Rock")

/** [set] with one plain (non-forced, non-SDH) regular track per language, in the order given. */
internal fun withSubtitles(set: MediaSet, vararg langs: String, alang: List<String> = emptyList()) =
    set.copy(subtitles = langs.mapIndexed { i, lang -> track(lang, index = i) }, alang = alang)

internal fun track(lang: String, forced: Boolean = false, sdh: Boolean = false, label: String = lang, index: Int = 0) =
    SubtitleTrackInfo(index, lang, forced, sdh, label)

internal fun cue(text: String) = TimedCue(0, 1_000, text)

/** The value of the Subtitles row currently marked on, or `null` for nothing regular showing ("off" is still a row, but a forced-only file offers none at all). */
internal fun selected(vm: PlayerViewModel) = vm.choices.value.subtitleOptions.firstOrNull { it.selected }?.value
