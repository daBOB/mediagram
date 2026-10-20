package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.round
import designsystem.Spacing
import ui.common.catalog.HeroArtwork

/** The title's backdrop where the picture would be, as dim as the web's `#cast-remote-art`. */
private const val ART_ALPHA = 0.35f

/**
 * The remote's frame: black, the title's [backdrop] dimmed where the picture
 * would be, the player's top bar with [title] and [label], the [notice] pill,
 * and the [card] it is handed. The TV box's remote and a player whose picture
 * is on a wireless display both draw it, so the two remotes look alike. Back
 * closes an open card menu before the library's own Back is reached.
 */
@Composable
internal fun RemoteFrame(
    title: String,
    backdrop: String?,
    label: String?,
    notice: String?,
    onBack: () -> Unit,
    card: @Composable BoxScope.(PlayerCardState) -> Unit,
) {
    val state = remember { PlayerCardState() }
    // Composed after the library's own Back, so an open menu closes first.
    BackHandler(enabled = state.somethingOpen, onBack = state::closeTopmost)
    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { state.origin = it.positionInRoot().round() }) {
        backdrop?.let { HeroArtwork(path = it, modifier = Modifier.matchParentSize().alpha(ART_ALPHA)) }
        Column(Modifier.align(Alignment.TopStart)) {
            PlayerTopBar(title = title, showTitle = true, onBack = onBack, onBottom = { state.topLimit = it }, label = label)
            TogetherNoticePill(notice, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.small))
        }
        card(state)
    }
}
