package ui.tv.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import player.PlayerChoices
import player.PlayerViewModel
import player.chooseAudioTrack
import player.chooseFraming
import player.chooseSubtitleLanguage
import player.nudgeSubtitleOffset
import player.resetSubtitleOffset
import player.setSpeed
import player.setSubtitleBacking
import player.setSubtitleSize
import ui.common.player.cardMenuOffset
import ui.common.player.playerCard
import ui.tv.rememberStableRequester

/** The controls' menus — one open at a time, each above the tool that opens it. */
internal enum class TvCardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }

/** Finds the open menu in a test. */
internal const val TvCardMenuTag = "tv-card-menu"

/** Room inside the padding for the subtitle style's sync row on one line. */
private val MenuWidth = 300.dp

/** A long language list scrolls rather than running off the top of a 540dp television. */
private val MenuMaxHeight = 320.dp

private val MenuGap = 8.dp

/**
 * The open [menu], just above the card at the tool that opened it and
 * inside the controls' width ([cardMenuOffset]), in the card's own fill. The remote
 * lands on the value already chosen, as a radio group opens on its
 * selection, and cannot wander out: the controls behind are still drawn,
 * and a Left meant for the next row would otherwise land on a button that
 * skips the film. Choosing closes it ([onClose]); Back closes it without
 * choosing — the player screen answers that, from the key table.
 *
 * Subtitle style is the one menu that stays open while it is used: size,
 * backing and sync are three settings judged together against the
 * picture, not one value to pick.
 */
@Composable
internal fun BoxScope.TvCardMenuOverlay(
    menu: TvCardMenu,
    choices: PlayerChoices,
    viewModel: PlayerViewModel,
    bands: TvStageBands,
    onClose: () -> Unit,
    onSwitch: (TvCardMenu) -> Unit,
) {
    val current = remember { FocusRequester() }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val gap = with(density) { MenuGap.roundToPx() }
    // Up to the overscan margin, over the title if it must be, as the web's menu grows over its top bar: 540 dp
    // leaves no room under the title for six speeds, the title is only read, and the remote cannot leave an open
    // menu for anything it covers. Never past that margin: a longer list scrolls inside itself.
    val room = bands.card?.let { card -> with(density) { (card.top - origin.y - gap).toDp() - Overscan.vertical }.coerceAtLeast(0.dp) } ?: MenuMaxHeight
    LaunchedEffect(menu) { current.requestFocus() }
    // The stage's own corner, which the notes column can move off the root's.
    Box(modifier = Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot().round() })
    Column(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { placed(bands.openers[menu], bands.card, origin, size, gap) }
                .width(MenuWidth)
                .heightIn(max = minOf(MenuMaxHeight, room))
                .onSizeChanged { size = it }
                .testTag(TvCardMenuTag)
                .playerCard()
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.medium),
    ) {
        when (menu) {
            TvCardMenu.Speed -> TvSpeedSection(speed = choices.speed, onChosen = { viewModel.setSpeed(it); onClose() }, current = current)
            TvCardMenu.Audio -> TvAudioSection(options = choices.audioOptions, onChosen = { viewModel.chooseAudioTrack(it); onClose() }, current = current)
            TvCardMenu.Framing -> TvFramingSection(framing = choices.framing, onChosen = { viewModel.chooseFraming(it); onClose() }, current = current)
            TvCardMenu.Subtitles -> TvSubtitleMenu(choices, viewModel, current, onClose, onSwitch)
            TvCardMenu.SubtitleStyle ->
                TvSubtitleStyleSection(
                    sizePercent = choices.subtitleSizePercent,
                    onSizeChosen = viewModel::setSubtitleSize,
                    backing = choices.subtitleBacking,
                    onBackingChosen = viewModel::setSubtitleBacking,
                    offsetMs = choices.subtitleOffsetMs,
                    onNudge = viewModel::nudgeSubtitleOffset,
                    onResetOffset = viewModel::resetSubtitleOffset,
                    current = current,
                )
        }
    }
}

/**
 * CC ▾: the languages and Off, then "Style…" into the style menu — where a
 * forced-only title, with no language to pick, opens straight away.
 */
@Composable
private fun TvSubtitleMenu(
    choices: PlayerChoices,
    viewModel: PlayerViewModel,
    current: FocusRequester,
    onClose: () -> Unit,
    onSwitch: (TvCardMenu) -> Unit,
) {
    val languages = choices.subtitleOptions.isNotEmpty()
    if (languages) {
        TvSubtitleSection(options = choices.subtitleOptions, onChosen = { viewModel.chooseSubtitleLanguage(it); onClose() }, current = current)
    }
    if (choices.subtitleStyleVisible) {
        TvOverlayButton(
            text = "Style…",
            style = TvTypeScale.body,
            enabled = true,
            onClick = { onSwitch(TvCardMenu.SubtitleStyle) },
            modifier = Modifier.fillMaxWidth().focusRequester(rememberStableRequester(current.takeUnless { languages })),
            padding = Spacing.medium,
        )
    }
}

/** [cardMenuOffset] in the stage's coordinates; the stage's corner until the controls and the tool have been placed. */
private fun placed(
    anchor: IntRect?,
    card: IntRect?,
    origin: IntOffset,
    size: IntSize,
    gap: Int,
): IntOffset = if (anchor == null || card == null) IntOffset.Zero else cardMenuOffset(anchor.translate(-origin), card.translate(-origin), size, gap)
