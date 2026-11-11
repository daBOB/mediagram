package com.mediagram.spieltisch

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import designsystem.Overscan
import designsystem.TvType
import designsystem.TvTypeScale
import designsystem.tvDp

/**
 * The games as a row of posters, the film shelves' plate width and gap, over the focused game's
 * backdrop. [focused] is held by the caller so coming back from a game lands on the poster that opened it.
 * [note], when there is one, is a line under the game's name about the night as a whole.
 */
@Composable
internal fun TvGamesShelf(
    games: List<ShelfGame>,
    focused: Int,
    note: String?,
    onFocus: (Int) -> Unit,
    onPick: (ShelfGame) -> Unit,
) {
    val game = games[focused]
    // More games than one screen holds: the row scrolls (as the film rows do), and the poster to
    // come back to is scrolled in before it takes focus, since a poster off screen isn't composed.
    val arrival = remember { focused }
    val listState = rememberLazyListState()
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        listState.scrollToItem(arrival)
        requester.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GameBackdrop(game.art?.backdrop)
        Column(Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical + 24.tvDp)) {
            Text("Game night", style = TvType.Eyebrow, color = OnImage.copy(alpha = 0.8f))
            Text(game.name, style = TvType.headlineSmall.copy(fontSize = TvTypeScale.FranchiseTitle), color = OnImage)
            Text(game.players, style = TvType.bodyMedium, color = OnImage.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.tvDp))
            note?.let { Text(it, style = TvType.bodyMedium, color = OnImage.copy(alpha = 0.8f), modifier = Modifier.padding(top = 12.tvDp)) }
        }
        BoxWithConstraints(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical + 12.tvDp),
        ) {
            val plate = stripPlateWidth(maxWidth)
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(StripGap)) {
                itemsIndexed(games, key = { _, g -> g.kind }) { i, g ->
                    val arrive = if (i == arrival) Modifier.focusRequester(requester) else Modifier
                    GamePoster(
                        game = g,
                        onPick = { onPick(g) },
                        modifier = Modifier.width(plate).then(arrive).onFocusChanged { if (it.isFocused) onFocus(i) },
                    )
                }
            }
        }
    }
}

/**
 * The focused game's backdrop, the one before it held underneath while it fades in. The left and bottom
 * shading for the text and the row is baked into the images, and the fade is the image's own draw alpha:
 * drawn shading and Crossfade's layers each cost the box a full-screen pass a frame (~44 ms frames measured).
 * Coil decodes off the main thread; a 1080p backdrop decoded in composition stalls every move.
 */
@Composable
private fun GameBackdrop(@DrawableRes backdrop: Int?) {
    var shown by remember { mutableStateOf(backdrop) }
    var under by remember { mutableStateOf<Int?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(backdrop) {
        if (backdrop == shown) return@LaunchedEffect
        under = shown
        shown = backdrop
        fade.snapTo(0f)
        fade.animateTo(1f, tween(400))
        under = null
    }
    // With nothing to fade in (a game without a backdrop), the old one fades out instead.
    under?.let { Backdrop(it, alpha = if (shown == null) 1f - fade.value else 1f) }
    shown?.let { Backdrop(it, alpha = fade.value) }
}

@Composable
private fun Backdrop(
    @DrawableRes res: Int,
    alpha: Float,
) {
    AsyncImage(model = res, contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha, modifier = Modifier.fillMaxSize())
}

@Composable
private fun GamePoster(
    game: ShelfGame,
    onPick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onPick,
        modifier = modifier.aspectRatio(PosterAspectRatio).semantics(mergeDescendants = true) { contentDescription = game.name },
        shape = CardDefaults.shape(RectangleShape),
        scale = CardDefaults.scale(focusedScale = FocusScale, pressedScale = FocusScale),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(FocusBorder, MaterialTheme.colorScheme.primary), shape = RectangleShape)),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The poster carries its own title; a game without one shows its name.
            if (game.art != null) {
                AsyncImage(model = game.art.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Text(
                    text = game.name,
                    style = TvType.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.tvDp),
                )
            }
        }
    }
}
