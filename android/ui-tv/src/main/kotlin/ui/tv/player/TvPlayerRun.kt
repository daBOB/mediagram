package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import player.PlayerViewModel

/**
 * What came before [setId] in [run], or `null` at its start or for a
 * title the run does not hold — `nextInQueue`'s mirror image.
 */
internal fun previousInRun(
    run: List<String>,
    setId: String,
): String? {
    val at = run.indexOf(setId)
    return if (at <= 0) null else run[at - 1]
}

/**
 * The remote's Next and Previous: one step forward or back through the
 * run the open title was started on.
 *
 * Next is the phone's own "Play next" — the up-next controller's switch,
 * which saves where the ending title stood and then asks for the move
 * through `onSwitch`, as the card's Play now does.
 *
 * Previous is the step back through the same run, and only that. Neither
 * the web nor the phone has a Previous of its own to match, so there is no
 * "restart the title if it is some way in" rule to follow; the one such
 * rule anywhere on this device is the playback session's, and it is the
 * one this key was taken away from — it sent a viewer who asked for the
 * last episode back to 0:00 of this one. The seek bar still goes back to
 * the start of a title for a viewer who wants that. It saves first, as
 * the switch forward does, so the title being left keeps its place on
 * Continue; the title it opens resumes where it was left, as opening it
 * from its plate would.
 *
 * At either end of the run a press does nothing, and is still taken (see
 * [TvPlayerRemote]).
 */
internal class TvRunSteps(
    val next: () -> Unit,
    val previous: () -> Unit,
)

@Composable
internal fun rememberTvRunSteps(
    viewModel: PlayerViewModel,
    setId: String,
    run: List<String>,
    onSwitch: (setId: String, run: List<String>) -> Unit,
): TvRunSteps {
    val openId by rememberUpdatedState(setId)
    val openRun by rememberUpdatedState(run)
    val switch by rememberUpdatedState(onSwitch)
    return remember(viewModel) {
        TvRunSteps(
            next = viewModel::playNext,
            previous = {
                previousInRun(openRun, openId)?.let { before ->
                    viewModel.save()
                    switch(before, openRun)
                }
            },
        )
    }
}
