package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.speedLabel

/**
 * The controls that do not move the film, kept together at the end of the
 * marks row: Notes while the title has any, the settings gear with the
 * speed beside it while it is not the default — where the phone and the
 * web both put that number, so a viewer looks for it in one place on every
 * surface — and, last, the statistics toggle, which only reports.
 *
 * One group, so that where the row has to wrap on a narrowed stage the
 * tools go to the next line together rather than splitting the gear from
 * its speed.
 */
@Composable
internal fun TvToolGroup(
    focus: TvPlayerFocus,
    extras: TvPlayerExtras,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        // The phone's top-bar "Notes", here in a row the remote reaches:
        // first of the tools, as it is the one a lesson is watched for.
        extras.onToggleNotes?.let { toggle ->
            TvOverlayButton(
                text = "Notes",
                style = TvTypeScale.body,
                enabled = true,
                onClick = toggle,
                modifier = Modifier.focusRequester(focus.notes),
                padding = Spacing.medium,
            )
        }
        if (extras.speed != 1f) {
            Text(text = speedLabel(extras.speed), style = TvTypeScale.body, color = Palette.Text)
        }
        TvGlyphButton(
            glyph = "⚙",
            description = "Playback settings",
            enabled = true,
            onClick = extras.onOpenSettings,
            modifier = Modifier.focusRequester(focus.settings),
            padding = Spacing.medium,
        )
        // Last, because it neither moves the film nor changes how it
        // plays. Named for which way the press goes, as play/pause is: a
        // glyph that stays put while what it does reverses tells a screen
        // reader nothing about which it is about to do.
        TvGlyphButton(
            glyph = "ⓘ",
            description = if (extras.statsShown) "Hide playback statistics" else "Show playback statistics",
            enabled = true,
            onClick = extras.onToggleStats,
            padding = Spacing.medium,
        )
    }
}
