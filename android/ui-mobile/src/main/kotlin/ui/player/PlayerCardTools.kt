package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.roundToIntRect
import designsystem.Spacing
import player.PlayerChoices
import player.speedLabel

/**
 * The card's middle row: what changes how the title plays, never where it is.
 *
 * CC turns regular subtitles on and off in one press, and is disabled rather
 * than hidden for a title without a regular track, so the row does not shift
 * from one title to the next; ▾ beside it opens the languages and the style,
 * and stays open to a forced-only title, whose lines still take a size.
 * Speed and Framing name the current choice. Audio is left out for a single
 * track, which is a label, not a choice. Picture-in-picture rides at the end
 * on a phone, as volume and fullscreen do on the web.
 *
 * Wraps rather than squeezes: on a 360dp phone a row of 48dp targets does
 * not fit one line.
 */
@Composable
internal fun CardToolsRow(
    choices: PlayerChoices,
    actions: PlayerCardActions,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "CC",
            description = "Subtitles",
            enabled = choices.ccVisible,
            onClick = actions.onToggleSubtitles,
            dimmed = !choices.subtitlesOn,
        )
        GlyphButton(
            glyph = "▾",
            description = "Subtitle options",
            enabled = choices.ccVisible || choices.subtitleStyleVisible,
            onClick = { actions.onOpenMenu(CardMenu.Subtitles) },
            modifier = actions.anchorFor(CardMenu.Subtitles),
        )
        LabelButton(
            label = speedLabel(choices.speed),
            description = "Speed",
            onClick = { actions.onOpenMenu(CardMenu.Speed) },
            modifier = actions.anchorFor(CardMenu.Speed),
        )
        if (choices.audioOptions.isNotEmpty()) {
            LabelButton(
                label = "Audio",
                description = "Audio",
                onClick = { actions.onOpenMenu(CardMenu.Audio) },
                modifier = actions.anchorFor(CardMenu.Audio),
            )
        }
        LabelButton(
            label = choices.framing.label,
            description = "Framing",
            onClick = { actions.onOpenMenu(CardMenu.Framing) },
            modifier = actions.anchorFor(CardMenu.Framing),
        )
        actions.onEnterPip?.let { enter ->
            GlyphButton(glyph = "⧉", description = "Picture in picture", enabled = true, onClick = enter)
        }
    }
}

/** Reports where [menu]'s button sits, in root coordinates, so the menu can be drawn directly above it. */
private fun PlayerCardActions.anchorFor(menu: CardMenu): Modifier =
    Modifier.onGloballyPositioned { onAnchor(menu, it.boundsInRoot().roundToIntRect()) }
