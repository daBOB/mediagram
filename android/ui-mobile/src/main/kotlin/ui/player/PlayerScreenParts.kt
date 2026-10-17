// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import player.ReportStage
import player.ReportUi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.Player
import designsystem.Accent
import designsystem.CountdownLeader
import designsystem.LocalCountdownStart
import designsystem.Spacing
import model.MediaSet
import playback.PlaybackTotals
import player.titleLine
import player.together.TogetherUi

/**
 * The pieces `PlayerScreen` draws, apart from the screen that arranges
 * them. Each is handed what it needs and decides nothing about when it
 * appears; the decisions stay with the screen and with
 * [ControlsVisibility]'s own functions, which is where they can be read
 * and proved.
 */

/**
 * The stage while the title opens: the film leader alone, centred, as the
 * web's stage shows it (`index.html:147`, `film-leader.css:56-58`). Its sweep
 * is the stage's fixed coral (`--stage-accent`, `theme.css:97`;
 * `film-leader.css:34`), whatever the page's accent, because the stage is
 * always black. The leader says nothing, so TalkBack hears "Loading" here.
 */
@Composable
internal fun PreparingNote() {
    Box(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center,
    ) {
        // A wait of its own, so its count starts at 3, not where the cold start's had got to.
        CompositionLocalProvider(LocalCountdownStart provides null) { CountdownLeader(accent = Accent.CORAL.dark) }
    }
}

/**
 * The back arrow, the title, the marks and Notes beside it, watch
 * together's label and notice ([together]), and the statistics. Absent entirely in picture-in-picture: there
 * is no touch surface of this app's own inside that window, and no room
 * for the statistics either.
 *
 * Placed by its caller with `Modifier.align(Alignment.TopStart)` rather
 * than centred here: the picture is what a `Box` centres its children
 * against, and the top bar has to be pinned to its own corner instead.
 */
@Composable
internal fun PlayerTopChrome(
    openSet: MediaSet?,
    barShown: Boolean,
    statsShown: Boolean,
    isInPip: Boolean,
    player: Player?,
    totals: () -> PlaybackTotals,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    onNotes: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    report: ReportUi = ReportUi(),
    marks: @Composable () -> Unit = {},
    onBarBottom: (Int) -> Unit = {},
    together: TogetherUi = TogetherUi(),
    sidebarBeside: Boolean = false,
) {
    // Watch together's notice goes too, unlike the web's beside its sidebar:
    // a sidebar that covers the window covers the picture it speaks of.
    if (isInPip) return
    Column(modifier = modifier) {
        PlayerTopBar(
            title = titleLine(openSet),
            showTitle = barShown,
            onBack = onBack,
            onNotes = onNotes,
            onReport = onReport,
            reportSent = report.stage == ReportStage.SENT,
            reportSending = report.stage == ReportStage.SENDING,
            marks = marks,
            onBottom = onBarBottom,
            label = together.label,
            sidebarBeside = sidebarBeside,
        )
        // Under the bar rather than with it, so it shows while the controls rest.
        TogetherNoticePill(together.notice, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.small))
        // Gated on the bar being shown as well as on the toggle, so the
        // statistics have no visibility rule of their own: a viewer who
        // leaves the numbers on gets the picture back when the card takes
        // itself away, and keeps them while the film is paused.
        if (statsShown && barShown) {
            player?.let { current ->
                PlaybackStatsOverlay(player = current, totals = totals, held = held, modifier = Modifier.padding(start = Spacing.medium))
            }
        }
    }
}
