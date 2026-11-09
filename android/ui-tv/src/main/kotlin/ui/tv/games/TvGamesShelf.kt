package ui.tv.games

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mediagram.android.ui.tv.R
import designsystem.Overscan
import designsystem.TvType
import designsystem.TvTypeScale
import designsystem.tvDp
import ui.common.catalog.ArtTileScrim
import ui.tv.TvFocus
import ui.tv.catalog.PosterAspectRatio
import ui.tv.catalog.StripGap
import ui.tv.catalog.home.OnImage
import ui.tv.catalog.stripPlateWidth

/** One game on the shelf: [kind] is the host's name for it (`GameKind`), the rest is what the TV shows. */
internal data class ShelfGame(
    val kind: String,
    val name: String,
    val players: String,
    @DrawableRes val poster: Int?,
    @DrawableRes val backdrop: Int?,
)

// Seats as the host counts them (room_settings.rs); a game without its own art yet shows its name.
internal val ShelfGames =
    listOf(
        ShelfGame("quiz", "Buzzer-Quiz", "1–8 players", R.drawable.ui_tv_game_poster_quiz, R.drawable.ui_tv_game_backdrop_quiz),
        ShelfGame("poster", "Poster-Raten", "1–8 players", R.drawable.ui_tv_game_poster_poster, R.drawable.ui_tv_game_backdrop_poster),
        ShelfGame("kopfnuss", "Kopfnuss", "2–8 players", null, null),
        ShelfGame("dreifelder", "Dreifelder", "2–8 players", null, null),
        ShelfGame("race", "Heimlauf", "2–4 players", null, null),
        ShelfGame("mau_mau", "Mau-Mau", "2–8 players", R.drawable.ui_tv_game_poster_mau_mau, R.drawable.ui_tv_game_backdrop_mau_mau),
        ShelfGame("schwimmen", "Schwimmen", "2–8 players", R.drawable.ui_tv_game_poster_schwimmen, R.drawable.ui_tv_game_backdrop_schwimmen),
        ShelfGame("durak", "Durak", "2–6 players", R.drawable.ui_tv_game_poster_durak, R.drawable.ui_tv_game_backdrop_durak),
        ShelfGame("grand_allee", "Grand Allee", "2–8 players", null, null),
    )

/**
 * The games as a row of posters, the film shelves' plate width and gap, over the focused game's
 * backdrop. [focused] is held by the caller so coming back from a game lands on the poster that opened it.
 */
@Composable
internal fun TvGamesShelf(
    focused: Int,
    onFocus: (Int) -> Unit,
    onPick: (ShelfGame) -> Unit,
) {
    val ground = MaterialTheme.colorScheme.background
    val game = ShelfGames[focused]
    // More games than one screen holds: the row scrolls (as the film rows do), and the poster to
    // come back to is scrolled in before it takes focus, since a poster off screen isn't composed.
    val arrival = remember { focused }
    val listState = rememberLazyListState()
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        listState.scrollToItem(arrival)
        requester.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(ground)) {
        GameBackdrop(game.backdrop)
        Column(Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical + 24.tvDp)) {
            Text("Game night", style = TvType.Eyebrow, color = OnImage.copy(alpha = 0.8f))
            Text(game.name, style = TvType.headlineSmall.copy(fontSize = TvTypeScale.FranchiseTitle), color = OnImage)
            Text(game.players, style = TvType.bodyMedium, color = OnImage.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.tvDp))
        }
        BoxWithConstraints(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical + 12.tvDp),
        ) {
            val plate = stripPlateWidth(maxWidth)
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(StripGap)) {
                itemsIndexed(ShelfGames, key = { _, g -> g.kind }) { i, g ->
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
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The poster carries its own title; without one, the backdrop's right side (where its scene is) and the name.
            val art = game.poster ?: game.backdrop
            if (art != null) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, modifier = Modifier.matchParentSize())
            }
            if (game.poster == null) {
                if (art != null) ArtTileScrim(false, Modifier.matchParentSize())
                Text(
                    text = game.name,
                    style = TvType.titleMedium,
                    color = if (art != null) OnImage else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.tvDp),
                )
            }
        }
    }
}
