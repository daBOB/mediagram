package ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import playback.AudioOption
import playback.Framing
import player.PlayerChoices

/** Finds an open card menu in a test. */
internal const val CardMenuTag = "player-card-menu"

/** Wide enough for the longest audio label; narrower than a phone's card, so a menu always fits inside it. */
private val MENU_MAX_WIDTH = 320.dp

/** Past this a menu scrolls — a file can carry a dozen subtitle languages. */
private val MENU_MAX_HEIGHT = 360.dp

/** What choosing in a card menu does — one bundle, as [PlayerMarksActions] is for the marks. */
internal class CardMenuActions(
    val onSpeed: (Float) -> Unit,
    val onAudio: (AudioOption) -> Unit,
    val onSubtitle: (String) -> Unit,
    val onSize: (Int) -> Unit,
    val onBacking: (String) -> Unit,
    val onNudge: (Int) -> Unit,
    val onResetOffset: () -> Unit,
    val onFraming: (Framing) -> Unit,
)

/**
 * One card menu's rows on the card's own fill. The rows are the Speed, Audio,
 * Subtitle and Framing sections, unchanged. Choosing a value closes the menu
 * ([onDone]); the subtitle style panel is the exception — a size, a backing
 * or a sync nudge is judged against the film a step at a time, so it stays
 * until Back. "Style…" moves this same menu to the style panel ([onOpen]).
 */
@Composable
internal fun CardMenuPanel(
    menu: CardMenu,
    choices: PlayerChoices,
    actions: CardMenuActions,
    onOpen: (CardMenu) -> Unit,
    onDone: () -> Unit,
    /** What the menu may grow to: on a short screen, the room above its button. */
    maxHeight: Dp = MENU_MAX_HEIGHT,
    modifier: Modifier = Modifier,
) {
    // Focus (and so a screen reader) moves into the menu as it opens.
    val focus = remember { FocusRequester() }
    LaunchedEffect(menu) { focus.requestFocusIfOnScreen() }
    // The sections draw in the content colour; on the card's dark fill that has to be white.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier =
                modifier
                    .widthIn(max = MENU_MAX_WIDTH)
                    .heightIn(max = maxHeight)
                    .testTag(CardMenuTag)
                    .focusRequester(focus)
                    .focusable()
                    .playerCard()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        ) {
            when (menu) {
                CardMenu.Speed -> SpeedSection(speed = choices.speed, onChosen = { actions.onSpeed(it); onDone() })
                CardMenu.Audio -> AudioSection(options = choices.audioOptions, onChosen = { actions.onAudio(it); onDone() })
                CardMenu.Framing -> FramingSection(framing = choices.framing, onChosen = { actions.onFraming(it); onDone() })
                CardMenu.Subtitles -> {
                    if (choices.subtitleOptions.isNotEmpty()) {
                        SubtitleSection(options = choices.subtitleOptions, onChosen = { actions.onSubtitle(it); onDone() })
                    }
                    if (choices.subtitleStyleVisible) StyleRow(onClick = { onOpen(CardMenu.SubtitleStyle) })
                }
                CardMenu.SubtitleStyle ->
                    SubtitleStyleSection(
                        sizePercent = choices.subtitleSizePercent,
                        onSizeChosen = actions.onSize,
                        backing = choices.subtitleBacking,
                        onBackingChosen = actions.onBacking,
                        offsetMs = choices.subtitleOffsetMs,
                        onNudge = actions.onNudge,
                        onResetOffset = actions.onResetOffset,
                    )
            }
        }
    }
}

/** The subtitle menu's way into the style panel, a full touch target like every other control over the picture. */
@Composable
private fun StyleRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Style…")
    }
}

/**
 * The open menu, drawn over the stage just above the card, at the button that
 * opened it and inside the card's width ([cardMenuOffset]). Every bound is in root
 * coordinates, so the stage's own place in the root is taken off. Drawn
 * clear until it has measured itself, so it never shows for a frame at the
 * wrong height.
 */
@Composable
internal fun BoxScope.CardMenuOverStage(
    card: PlayerCardState,
    choices: PlayerChoices,
    actions: CardMenuActions,
) {
    val open = card.menu ?: return
    val anchor = card.anchorOf(open) ?: return
    val bounds = card.bounds ?: return
    var size by remember(open) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val gap = with(density) { Spacing.small.roundToPx() }
    // Never taller than the room between the top bar and the card, so a short screen scrolls the menu instead of covering either.
    val room = with(density) { (bounds.top - gap - card.topLimit).coerceAtLeast(0).toDp() }
    CardMenuPanel(
        menu = open,
        choices = choices,
        actions = actions,
        onOpen = card::switchTo,
        onDone = card::closeMenu,
        maxHeight = minOf(MENU_MAX_HEIGHT, room),
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { cardMenuOffset(anchor, bounds, size, gap) - card.origin }
                .onSizeChanged { size = it }
                .alpha(if (size == IntSize.Zero) 0f else 1f),
    )
}
