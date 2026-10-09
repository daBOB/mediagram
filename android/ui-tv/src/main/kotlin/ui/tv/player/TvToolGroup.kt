package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.roundToIntRect
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import player.speedLabel
import player.together.TogetherUi
import player.together.description
import player.together.words

/**
 * The card's middle row: its tools ([TvCardTools]), wrapping rather than
 * running off a stage the notes column has narrowed. From anywhere along
 * it, Up is the seek bar and Down is play/pause — named, since the row is
 * shorter than the ones either side of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvToolGroup(
    focus: TvPlayerFocus,
    extras: TvPlayerExtras,
    bands: TvStageBands,
    speed: Float,
) {
    val between =
        Modifier.focusProperties {
            this.up = focus.seekBar
            this.down = focus.playPause
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TvCardTools(focus = focus, extras = extras, bands = bands, speed = speed, each = between)
    }
}

/**
 * The card's tools, in the web's order: CC, which turns subtitles on or off
 * in one press, and ▾ beside it for the languages and their style; then
 * Speed, Audio — only with more than one track to choose from — and
 * Framing, each opening a short menu of the rest above itself. Speed names
 * the [speed] playing, as the web's does: in watch together it can be
 * another screen's while the menu marks this viewer's choice. Audio and
 * Framing name what is chosen. CC with no regular track is drawn but dimmed, still there
 * to be read with nothing to turn on, and ▾ the same with no track at all.
 * Watch together ends the row, as on the web ([TvTogetherTool]).
 *
 * [each] is laid on every one of them: where the remote goes from the row.
 * Left from CC and Right from Watch together do nothing: they end the row.
 */
@Composable
internal fun TvCardTools(
    focus: TvPlayerFocus,
    extras: TvPlayerExtras,
    bands: TvStageBands,
    speed: Float,
    each: Modifier = Modifier,
) {
    val choices = extras.choices
    val on = choices.subtitlesOn
    TvGlyphButton(
        glyph = if (on) "CC ●" else "CC ○",
        description = "Subtitles",
        enabled = choices.ccVisible,
        onClick = extras.onToggleSubtitles,
        modifier = each.focusProperties { left = FocusRequester.Cancel }.focusRequester(focus.cc).semantics { stateDescription = if (on) "On" else "Off" },
        padding = Spacing.small,
    )
    TvGlyphButton(
        glyph = "▾",
        description = "Subtitle options",
        enabled = choices.ccVisible || choices.subtitleStyleVisible,
        onClick = { extras.onOpenMenu(TvCardMenu.Subtitles) },
        modifier = each.focusRequester(focus.subtitleOptions).opens(bands, TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle),
        padding = Spacing.small,
    )
    TvMenuTool(speedLabel(speed), "Speed", each.focusRequester(focus.speed).opens(bands, TvCardMenu.Speed)) { extras.onOpenMenu(TvCardMenu.Speed) }
    if (choices.audioOptions.isNotEmpty()) {
        TvMenuTool("Audio", "Audio", each.focusRequester(focus.audio).opens(bands, TvCardMenu.Audio)) { extras.onOpenMenu(TvCardMenu.Audio) }
    }
    TvMenuTool(choices.framing.label, "Framing", each.focusRequester(focus.framing).opens(bands, TvCardMenu.Framing)) { extras.onOpenMenu(TvCardMenu.Framing) }
    TvTogetherTool(extras.together, each.focusProperties { right = FocusRequester.Cancel }.focusRequester(focus.together), extras.onTogether)
}

/**
 * Watch together, Join or Leave watch together, as the web's tool says.
 * Greyed out it still takes the remote and gives the reason as a second
 * line, where the web shows it on hover — a television has no hover.
 */
@Composable
private fun TvTogetherTool(
    ui: TogetherUi,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TvOverlaySurface(onClick = onClick, enabled = ui.enabled, modifier = modifier.semantics { contentDescription = ui.description }) {
        Column(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
            Text(text = ui.button.words, style = TvTypeScale.body)
            ui.reason?.let { Text(text = it, style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow)) }
        }
    }
}

@Composable
private fun TvMenuTool(
    label: String,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TvOverlayButton(
        text = label,
        style = TvTypeScale.body,
        enabled = true,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        padding = Spacing.medium,
    )
}

/** Reports where this tool is, so each of [menus] opens just above it. Written only on a change: the bands are read during layout. */
private fun Modifier.opens(
    bands: TvStageBands,
    vararg menus: TvCardMenu,
): Modifier =
    onGloballyPositioned { at ->
        val bounds = at.boundsInRoot().roundToIntRect()
        for (menu in menus) if (bands.openers[menu] != bounds) bands.openers[menu] = bounds
    }
