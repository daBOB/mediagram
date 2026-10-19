// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.round
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlaybackSpeedState
import designsystem.Spacing
import kotlinx.coroutines.delay
import model.MediaSet
import playback.AudioOption
import player.CastRemoteViewModel
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.UpNextUiState
import player.together.RemoteUi
import ui.common.catalog.HeroArtwork
import ui.common.player.findActivity

/** The title's backdrop where the picture would be, as dim as the web's `#cast-remote-art`. */
private const val ART_ALPHA = 0.35f

/** How long the words a cast ended with stay before the remote goes: the web's notice. */
private const val OVER_MS = 2_000L

/**
 * The remote of cast [id], as the library opens it: joins the cast as this
 * screen opens and leaves it as the screen goes for good, the TV playing on.
 * Next episode follows [runOf] a title's run, as the player's does.
 */
@Composable
fun CastRemoteScreen(
    id: Int,
    runOf: (MediaSet) -> List<String>,
    onBack: () -> Unit,
    remote: CastRemoteViewModel = hiltViewModel(),
) {
    val ui by remote.ui.collectAsStateWithLifecycle()
    val latestRunOf by rememberUpdatedState(runOf)
    val activity = LocalContext.current.findActivity()
    DisposableEffect(id) {
        remote.open(id) { latestRunOf(it) }
        // The model outlives a recreated activity: leaving and joining again would be noticed on the TV.
        onDispose { if (activity?.isChangingConfigurations != true) remote.close() }
    }
    CastRemote(
        // An ended cast's last words stay in the model until another one draws.
        ui = ui?.takeIf { it.id == id },
        player = remote.player,
        onNext = remote::next,
        onAudio = remote::chooseAudio,
        onText = remote::chooseText,
        onStop = remote::stop,
        onBack = onBack,
    )
}

/**
 * The web's remote on the player's own parts: the top bar with the title and
 * "Playing on TV", the notice pill, and the control card over the title's
 * dimmed backdrop. The card's [player] is the TV's session; its subtitles
 * and audio menus list the TV's tracks. Nothing is drawn but the bar until
 * [ui] first arrives; once it goes, or says how the cast ended, the remote
 * goes too ([LeaveWithTheCast]).
 */
@Composable
internal fun CastRemote(
    ui: RemoteUi?,
    player: Player,
    onNext: () -> Unit,
    onAudio: (String) -> Unit,
    onText: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    LeaveWithTheCast(ui, onBack)
    val card = remember { PlayerCardState() }
    // Composed after the library's own Back, so an open menu closes first.
    BackHandler(enabled = card.somethingOpen, onBack = card::closeTopmost)
    val speed = rememberPlaybackSpeedState(player).playbackSpeed
    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { card.origin = it.positionInRoot().round() }) {
        ui?.backdrop?.let { HeroArtwork(path = it, modifier = Modifier.matchParentSize().alpha(ART_ALPHA)) }
        Column(Modifier.align(Alignment.TopStart)) {
            PlayerTopBar(title = ui?.title.orEmpty(), showTitle = true, onBack = onBack, onBottom = { card.topLimit = it }, label = ui?.label)
            TogetherNoticePill(ui?.over ?: ui?.notice, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.small))
        }
        if (ui != null) {
            val choices = remoteChoices(ui, speed)
            PlayerControlCard(
                player = player,
                view = PlayerCardView(choices, UpNextUiState(hasNext = ui.hasNext), statsShown = false, hasEpisodes = false, catalogedDurationSecs = null, openMenu = card.menu),
                actions =
                    PlayerCardActions(
                        onRestart = {}, onPrevious = {}, onNext = onNext,
                        // CC opens the TV's list, as the web remote's does: which track "on" means is the viewer's to say.
                        onToggleSubtitles = { card.toggle(CardMenu.Subtitles) },
                        onOpenMenu = card::toggle, onToggleStats = {}, onEpisodes = {}, onEnterPip = null,
                        onAnchor = card::anchor, focusOf = card::focusOf, onStop = onStop,
                    ),
                onScrubbingChanged = {},
                modifier = Modifier.align(Alignment.BottomCenter),
                onBounds = { card.bounds = it },
            )
            CardMenuOverStage(
                card,
                choices,
                CardMenuActions(
                    onSpeed = player::setPlaybackSpeed,
                    onAudio = { onAudio(ui.tracks.audio[it.trackIndex].id) },
                    onSubtitle = { onText(if (it == SUBTITLES_OFF) "" else it) },
                    onSize = {}, onBacking = {}, onNudge = {}, onResetOffset = {}, onFraming = {},
                ),
            )
        }
    }
}

/**
 * The TV's tracks as the card's menus list them: Off and its subtitles, and
 * its audio only when there is a choice of two. An audio row's `trackIndex`
 * is its place in the TV's list, which the choice is read back from.
 */
private fun remoteChoices(
    ui: RemoteUi,
    speed: Float,
) = PlayerChoices(
    speed = speed,
    audioOptions = ui.tracks.audio.takeIf { it.size > 1 }.orEmpty().mapIndexed { i, t -> AudioOption(0, i, null, t.label, t.id == ui.audio) },
    subtitleOptions =
        ui.tracks.text.takeIf { it.isNotEmpty() }?.let { text ->
            listOf(SubtitleOption(SUBTITLES_OFF, "Off", ui.text.isEmpty())) + text.map { SubtitleOption(it.id, it.label, it.id == ui.text) }
        }.orEmpty(),
)

/**
 * The remote goes when the cast does: at once once nothing is left to draw,
 * or [OVER_MS] after the words it ended with. Never before its first draw,
 * which arrives a moment after the screen opens.
 */
@Composable
private fun LeaveWithTheCast(
    ui: RemoteUi?,
    onGone: () -> Unit,
) {
    val gone by rememberUpdatedState(onGone)
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(ui == null, ui?.over) {
        when {
            ui?.over != null -> {
                delay(OVER_MS)
                gone()
            }
            ui != null -> drawn = true
            drawn -> gone()
        }
    }
}
