package ui.tv.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import designsystem.Overscan
import designsystem.Spacing
import model.MediaSet
import playback.TimedCue
import player.PlayerChoices
import player.PlayerViewModel
import player.UpNextPhase
import player.UpNextUiState

/**
 * Where the stage's bands are, in root coordinates, as they last reported
 * themselves: the top of the bottom controls ([barTop]), the bottom of the
 * title and statistics along the top ([topBottom]), the up-next card's top
 * ([cardTop]) and the settings panel's left edge ([panelLeft]). Each is
 * what one band tells the others so none is drawn over another; the last
 * two are `null` while their band is not on screen.
 */
internal class TvStageBands {
    var barTop by mutableStateOf<Float?>(null)
    var topBottom by mutableStateOf<Float?>(null)
    var cardTop by mutableStateOf<Float?>(null)
    var panelLeft by mutableStateOf<Float?>(null)
}

/** What the stage draws beside the controls: the film and its subtitles, and whether the controls and the settings panel are up. */
internal class TvStagePicture(
    val cues: List<TimedCue>,
    val choices: PlayerChoices,
    val barShown: Boolean,
    val settingsOpen: Boolean,
)

/**
 * The film and everything over it, in three bands: along the top what is
 * playing, along the bottom the controls, and floating between them the
 * subtitles and the up-next card — each kept clear of the others by where
 * they report themselves ([bands]). The settings panel stands down the
 * right while it is open, and what floats moves over to leave it room.
 */
@Composable
internal fun BoxScope.TvPlayerStage(
    player: Player,
    set: MediaSet?,
    focus: TvPlayerFocus,
    viewModel: PlayerViewModel,
    view: TvControlsView,
    actions: TvControlsActions,
    picture: TvStagePicture,
    bands: TvStageBands,
) {
    val shown = picture.barShown
    // The card stands just above the controls, where a lifted cue would go,
    // so while it shows the cue lifts clear of the card too — beside it, on
    // a stage the notes have narrowed, a long line would be squeezed into a
    // column too thin to read.
    val lowest = listOfNotNull(bands.barTop, bands.cardTop).minOrNull()
    val room = TvCueRoom(barTop = lowest.takeIf { shown }, ceiling = bands.topBottom.takeIf { shown }, besideLeft = bands.panelLeft)
    TvVideoWithSubtitles(player, picture.cues, picture.choices, room)
    if (shown) TvPlayerControlsForViewModel(player, set, focus, viewModel, view, actions, bands)
    if (shown && view.upNext.phase != UpNextPhase.HIDDEN) {
        TvUpNextOverStage(view.upNext, focus, actions.onPlayNext, viewModel::cancelUpNext, bands, besidePanel = picture.settingsOpen)
    }
    if (picture.settingsOpen) {
        TvPlayerSettingsPanel(
            choices = picture.choices,
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.CenterEnd).onGloballyPositioned { bands.panelLeft = it.boundsInRoot().left },
        )
        DisposableEffect(Unit) { onDispose { bands.panelLeft = null } }
    }
}

/**
 * The up-next card floating over the picture, as the web floats its own
 * and the phone clamps its own: at the bottom-right, just above the
 * controls ([TvStageBands.barTop]), never inside them — inside, it pushed
 * the whole block up by its own height, into the title and over the clock
 * of a narrowed stage. As wide as the stage can spare, up to the card's
 * own limit, and to the left of the settings panel while that is open, so
 * the countdown stays in view while a viewer changes a setting.
 */
@Composable
private fun BoxScope.TvUpNextOverStage(
    state: UpNextUiState,
    focus: TvPlayerFocus,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    bands: TvStageBands,
    besidePanel: Boolean,
) {
    var stageBottom by remember { mutableFloatStateOf(0f) }
    val lift = with(LocalDensity.current) { bands.barTop?.let { (stageBottom - it).coerceAtLeast(0f).toDp() } ?: Overscan.vertical }
    BoxWithConstraints(
        modifier =
            Modifier
                .matchParentSize()
                .onGloballyPositioned { stageBottom = it.boundsInRoot().bottom }
                .padding(end = if (besidePanel) TvSettingsPanelWidth else 0.dp),
    ) {
        TvUpNextCard(
            state = state,
            playNow = focus.upNext,
            onPlayNow = onPlayNow,
            onCancel = onCancel,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Overscan.horizontal, bottom = lift + Spacing.small)
                    .widthIn(max = (maxWidth - Overscan.horizontal * 2).coerceAtLeast(0.dp))
                    .onGloballyPositioned { bands.cardTop = it.boundsInRoot().top },
        )
    }
    DisposableEffect(Unit) { onDispose { bands.cardTop = null } }
}
