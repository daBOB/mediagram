package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
 * track, which is a label, not a choice. The tools stand at the left and
 * picture-in-picture at the right end, as volume and fullscreen do on the web.
 *
 * The tools wrap rather than squeeze: on a 360dp phone a row of 48dp targets
 * does not fit one line. [openMenu] is what a screen reader hears as each
 * opener being expanded or collapsed.
 */
@Composable
internal fun CardToolsRow(
    choices: PlayerChoices,
    actions: PlayerCardActions,
    openMenu: CardMenu? = null,
) {
    fun expanded(vararg menus: CardMenu) = if (openMenu in menus) "Expanded" else "Collapsed"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(
                glyph = "CC",
                description = "Subtitles",
                enabled = choices.ccVisible,
                onClick = actions.onToggleSubtitles,
                dimmed = !choices.subtitlesOn,
                state = if (choices.subtitlesOn) "On" else "Off",
            )
            GlyphButton(
                glyph = "▾",
                description = "Subtitle options",
                enabled = choices.ccVisible || choices.subtitleStyleVisible,
                onClick = { actions.onOpenMenu(CardMenu.Subtitles) },
                modifier = actions.openerFor(CardMenu.Subtitles),
                state = expanded(CardMenu.Subtitles, CardMenu.SubtitleStyle),
            )
            LabelButton(
                label = speedLabel(choices.speed),
                description = "Speed",
                onClick = { actions.onOpenMenu(CardMenu.Speed) },
                modifier = actions.openerFor(CardMenu.Speed),
                state = expanded(CardMenu.Speed),
            )
            if (choices.audioOptions.isNotEmpty()) {
                LabelButton(
                    label = "Audio",
                    description = "Audio",
                    onClick = { actions.onOpenMenu(CardMenu.Audio) },
                    modifier = actions.openerFor(CardMenu.Audio),
                    state = expanded(CardMenu.Audio),
                )
            }
            LabelButton(
                label = choices.framing.label,
                description = "Framing",
                onClick = { actions.onOpenMenu(CardMenu.Framing) },
                modifier = actions.openerFor(CardMenu.Framing),
                state = expanded(CardMenu.Framing),
            )
        }
        actions.onEnterPip?.let { enter ->
            GlyphButton(glyph = "⧉", description = "Picture in picture", enabled = true, onClick = enter)
        }
    }
}

/**
 * What an opener reports about itself: where its button sits, in root
 * coordinates, so the menu can be drawn directly above it, and the focus
 * target the menu hands focus back to when it closes.
 */
private fun PlayerCardActions.openerFor(menu: CardMenu): Modifier =
    Modifier
        .onGloballyPositioned { onAnchor(menu, it.boundsInRoot().roundToIntRect()) }
        .focusRequester(focusOf(menu))
