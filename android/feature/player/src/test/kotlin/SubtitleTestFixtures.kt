package player

import model.MediaSet
import playback.TimedCue

/** An episode keyed under `key:show-x` — the scope every subtitle test's remembered values sit under. */
internal val subtitledShow = fakeMediaSet(setId = "ep1", posterKey = "show-x", show = "30 Rock")

internal fun withSubtitles(set: MediaSet, vararg languages: String) =
    set.copy(subtitleLanguages = languages.toList())

internal fun cue(text: String) = TimedCue(0, 1_000, text)

/** The value of the Subtitles row currently marked on. */
internal fun selected(vm: PlayerViewModel) = vm.choices.value.subtitleOptions.first { it.selected }.value
