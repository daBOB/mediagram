package ui.catalog

import androidx.compose.animation.Crossfade
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import catalog.stepFrom
import designsystem.Motion
import designsystem.Spacing
import kotlinx.coroutines.delay
import model.MediaSet
import ui.common.catalog.rememberTitleInfo
import uniffi.mediagram_core.TitleInfo
import ui.chrome.NoDialogAnimation
import ui.chrome.IndicationInk
import ui.catalog.home.OnImage

/** How long a slide holds before the next — the web's `HOLD_MS`. */
private const val HOLD_MS = 7_000L

/** The crossfade between slides — the web's `FADE_MS`. */
internal const val FADE_MS = 1_200

/**
 * The Featured reel — `featured-reel.js` in the web player: films not yet
 * watched, one poster at a time, to help choose what to watch. Dark, like the
 * player, because it is the lobby of the screening room.
 *
 * Each slide holds for seven seconds and fades into the next; ‹ and › and
 * the dots step through by hand, and a tap on the poster pauses, as Space
 * does on the web. The reel stands still while the app is not in front, as
 * the web's does in a hidden tab. Back closes it, as the web's back button
 * does. Play and Details close it first and then act.
 */
@Composable
internal fun FeaturedReel(
    films: List<MediaSet>,
    titleInfo: suspend (String) -> TitleInfo?,
    onPlay: (MediaSet) -> Unit,
    onDetails: (MediaSet) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        NoDialogAnimation()
        var index by rememberSaveable { mutableIntStateOf(0) }
        var paused by rememberSaveable { mutableStateOf(false) }
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val visible = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        LaunchedEffect(index, paused, visible) {
            if (paused || !visible) return@LaunchedEffect
            delay(HOLD_MS)
            index = stepFrom(index, 1, films.size)
        }

        IndicationInk(OnImage) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // The web's `.featured-slide` opacity transition, `1200ms ease`
                // (`playback.css:477`). "Remove animations" scales it to nothing,
                // as the web's reduced motion removes it, and the poster's drift
                // reads the setting itself, so the reel needs no read of its own.
                Crossfade(targetState = index, animationSpec = tween(FADE_MS, easing = Motion.CssEase), label = "featured") { at ->
                    val set = films[at]
                    FeaturedSlide(
                        set = set,
                        count = "Featured · ${at + 1} / ${films.size}",
                        info = rememberTitleInfo(set.posterKey, titleInfo),
                        paused = paused,
                        onTogglePause = { paused = !paused },
                        onPlay = { onClose(); onPlay(set) },
                        onDetails = { onClose(); onDetails(set) },
                    )
                }
                CatalogueTextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .semantics { contentDescription = "Close" },
                ) { Text("✕", color = Color.White) }
                FeaturedNav(
                    films = films,
                    current = index,
                    onStep = { by -> index = stepFrom(index, by, films.size) },
                    onJump = { index = it },
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}

/** ‹, a dot per film, › — the web's `featured-nav`. */
@Composable
private fun FeaturedNav(
    films: List<MediaSet>,
    current: Int,
    onStep: (Int) -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        CatalogueTextButton(onClick = { onStep(-1) }, modifier = Modifier.semantics { contentDescription = "Previous film" }) {
            Text("‹", color = Color.White)
        }
        // Small targets rather than full buttons: twelve of those would not
        // fit across a phone held upright.
        films.forEachIndexed { at, set ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(DOT_TARGET)
                    .clickable(role = Role.Button) { onJump(at) }
                    .semantics { contentDescription = set.title },
            ) {
                Box(
                    Modifier
                        .size(DOT)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (at == current) 1f else 0.4f)),
                )
            }
        }
        CatalogueTextButton(onClick = { onStep(1) }, modifier = Modifier.semantics { contentDescription = "Next film" }) {
            Text("›", color = Color.White)
        }
    }
}

private val DOT = 8.dp

private val DOT_TARGET = 24.dp
