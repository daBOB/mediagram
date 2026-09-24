package player

import playback.AudioOption

/**
 * Thin pass-throughs onto [PlayerViewModel]'s controllers, plus [retry] —
 * split out to keep that file under the project's line guideline.
 */

/**
 * Re-opens the title that just failed, at wherever it was last saved to —
 * the phone's touch equivalent of the web's seek-to-retry. [PlayerViewModel.open]
 * takes its "same title" path here (nothing about the choice already made
 * needs re-resolving), but the failed player was left in `STATE_IDLE`, so
 * this *is* a real reload and does floor the rate to 1x inside the handle;
 * unlike a fresh title, nothing corrects that back on its own, so it is
 * corrected here.
 */
fun PlayerViewModel.retry() {
    val setId = session.openSetId ?: return
    open(setId, fsk = openFsk.value)
    handle.setPlaybackSpeed(choicesController.choices.value.speed)
}

fun PlayerViewModel.setSpeed(rate: Float) = choicesController.setSpeed(rate)
fun PlayerViewModel.chooseAudioTrack(option: AudioOption) = choicesController.chooseAudioTrack(option)
fun PlayerViewModel.chooseSubtitleLanguage(languageOrOff: String) = choicesController.chooseSubtitleLanguage(languageOrOff)
fun PlayerViewModel.setSubtitleSize(percent: Int) = choicesController.setSubtitleSize(percent)
fun PlayerViewModel.setSubtitleBacking(stored: String) = choicesController.setSubtitleBacking(stored)
fun PlayerViewModel.nudgeSubtitleOffset(steps: Int) = choicesController.nudgeSubtitleOffset(steps)
fun PlayerViewModel.resetSubtitleOffset() = choicesController.resetSubtitleOffset()

fun PlayerViewModel.toggleWatchlist() = marksController.toggleWatchlist()
fun PlayerViewModel.toggleKids() = marksController.toggleKids()
fun PlayerViewModel.setInList(listId: String, included: Boolean) = marksController.setInList(listId, included)
fun PlayerViewModel.createListAndAdd(name: String) = marksController.createListAndAdd(name)
