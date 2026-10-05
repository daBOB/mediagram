// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import designsystem.Spacing
import player.PlayerChoices
import player.READOUT_TICK_MS
import player.UpNextUiState
import player.endsLine

/** Finds the card in a test. */
internal const val PlayerCardTag = "player-card"

/** Past this a tablet's card reads as a strip across the picture rather than a card on it. */
private val CARD_MAX_WIDTH = 720.dp

/** The card's distance from the window's sides and bottom edge. */
private val CARD_MARGIN = 12.dp

/** What the card's buttons do, to the ViewModel and to the card's own state — one bundle, so its call site hands over one thing. */
internal class PlayerCardActions(
    val onRestart: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToggleSubtitles: () -> Unit,
    val onOpenMenu: (CardMenu) -> Unit,
    val onToggleStats: () -> Unit,
    val onEpisodes: () -> Unit,
    /** Null where picture-in-picture is not offered, which leaves its button out. */
    val onEnterPip: (() -> Unit)?,
    /** Where each menu's button sits, in root coordinates — what the menu is drawn above. */
    val onAnchor: (CardMenu, IntRect) -> Unit = { _, _ -> },
    /** The focus target of [CardMenu]'s opener, or of the episodes button for null — where focus returns when what it opened closes. */
    val focusOf: (CardMenu?) -> FocusRequester = { FocusRequester() },
)

/** What the card reads, beside the player itself. */
internal class PlayerCardView(
    val choices: PlayerChoices,
    val upNext: UpNextUiState,
    val statsShown: Boolean,
    /** Whether the open title has a run to list; ☰ is left out without one. */
    val hasEpisodes: Boolean,
    /** The catalogue's own runtime in whole seconds, trusted over media3's until it has one; see [endsLine]. */
    val catalogedDurationSecs: Int?,
    /** What a screen reader hears each opener report as expanded: the open menu, and whether the sidebar is out. */
    val openMenu: CardMenu? = null,
    val sidebarOpen: Boolean = false,
)

/**
 * The player's controls as one card at the bottom of the picture: where the
 * film is (row 1), how it plays (row 2, [CardToolsRow]) and what moves it
 * (row 3, [CardTransportRow]) — the web's card, filled with a flat dark tint
 * instead of its blur (see [playerCard] for why).
 *
 * Everything about the playhead is read through media3's own state holders
 * rather than carried through the ViewModel — see
 * `feature/player/build.gradle.kts` for where that line is drawn.
 *
 * Inset by the navigation bar and any side cutout, then by its own margin,
 * so a drag meant for the scrub bar never lands on a three-button
 * navigation bar. [onBounds] reports the card itself, margin excluded: the
 * subtitles lift clear of its top, and a menu stays inside its width.
 */
@Composable
internal fun PlayerControlCard(
    player: Player,
    view: PlayerCardView,
    actions: PlayerCardActions,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBounds: (IntRect) -> Unit = {},
) {
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)

    // Null except mid-drag, when it holds where the thumb is rather than
    // where the film is. A slider snapped back to the playhead twice a
    // second could not be dragged at all — the web solves this the same way.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrubbingTo == null) { onScrubbingChanged(scrubbingTo != null) }

    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val positionMs = scrubbingTo?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)
    // Counted from the playhead, not the scrub thumb, which only previews where a seek would land.
    val endsLabel =
        endsLine(
            cataloguedSecs = view.catalogedDurationSecs,
            positionMs = progress.currentPositionMs,
            durationMs = progress.durationMs,
            speed = view.choices.speed,
            nowMs = System.currentTimeMillis(),
        )

    Column(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(start = CARD_MARGIN, end = CARD_MARGIN, bottom = CARD_MARGIN)
                .widthIn(max = CARD_MAX_WIDTH)
                .fillMaxWidth()
                .onGloballyPositioned { onBounds(it.boundsInRoot().roundToIntRect()) }
                .testTag(PlayerCardTag)
                .playerCard()
                .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        PlayerScrubber(
            positionMs = positionMs,
            durationMs = durationMs,
            endsLabel = endsLabel,
            scrubbingTo = scrubbingTo,
            onScrubbingToChange = { scrubbingTo = it },
            onSeek = player::seekTo,
        )
        CardToolsRow(choices = view.choices, actions = actions, openMenu = view.openMenu)
        CardTransportRow(player = player, upNext = view.upNext, statsShown = view.statsShown, hasEpisodes = view.hasEpisodes, actions = actions, sidebarOpen = view.sidebarOpen)
    }
}
