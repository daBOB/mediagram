package ui.tv.catalog.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import model.MediaSet

/** The web's own `HOLD_MS` (`home-cover.js:20`) — the phone cover holds by the same clock. */
private const val HOLD_MS = 9_000L

/** The web's own crossfade duration (`home.css`'s `cover-fade`, `1400ms`) — the phone cover fades by the same clock. */
private const val CROSSFADE_MS = 1_400

/**
 * Room the cover leaves clear at the foot of a 540dp television screen so
 * its own buttons sit on the first screen with the features peeking below
 * — a deliberate, documented difference from the phone's own taller
 * `clamp(620px, 63.5vh, 705px)`, which a 540dp screen has no room for.
 */
private val CoverBarClearance = 72.dp

/**
 * The magazine home page's cover story, remote-first — the television twin
 * of the phone's `HomeCover` and the web's `home-cover.js`. One film at a
 * time, full width under the departments bar (which reads its own
 * translucency from the same list this draws into, through the shared
 * `ui.common.chrome.coverBlend`); rotates every [HOLD_MS] while no focus sits
 * anywhere inside it and while nothing has paused it by leaving — a
 * television has no hover to rest and hold it the way a pointer does, so
 * focus is what takes over that job here.
 *
 * [TvCoverActions] — the action row and the dots — draws as a sibling
 * outside [Crossfade], bound to whichever film [current] names: neither a
 * button nor a dot ever fades with the picture underneath it.
 *
 * [initialFilmId] is the one film this cover opens on — the caller's own
 * `homeTargetOf` resolution, already narrowed to "a stop in the cover, or
 * none" before this composable ever sees it; a missing or unrecognised id
 * (the film left the lineup between composing and now) falls back to the
 * first film rather than crashing on a lookup that found nothing.
 */
@Composable
internal fun TvHomeCover(
    films: List<MediaSet>,
    watchlist: Set<String>,
    initialFilmId: String?,
    onPlay: (MediaSet) -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatchlist: (String, Boolean) -> Unit,
    // Where the requester ends up (Watch now) — whether and when it is
    // actually asked to take focus is `TvHome`'s own call, made once after
    // its outer list has confirmed the cover is really composed, not this
    // composable's to decide on its own mount (a real regression once it
    // was: a request fired the instant this mounted, racing a sentinel
    // elsewhere handing the remote to Search or the bar's own ⋮ instead).
    arrivalFocus: FocusRequester,
    // Where Up from the action row leads once it runs out of the cover's
    // own subtree to search — the bar's own selected pill, the same stop
    // Back already reaches from anywhere in the page.
    upExit: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (films.isEmpty()) return
    var currentId by rememberSaveable { mutableStateOf(initialFilmId ?: films.first().setId) }
    var hasFocus by remember { mutableStateOf(false) }
    val current = films.firstOrNull { it.setId == currentId } ?: films.first()
    val rotates = films.size > 1

    if (rotates) {
        LaunchedEffect(currentId, hasFocus) {
            if (hasFocus) return@LaunchedEffect
            delay(HOLD_MS)
            val at = films.indexOfFirst { it.setId == currentId }.coerceAtLeast(0)
            currentId = films[(at + 1) % films.size].setId
        }
    }

    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    val coverHeight = (windowHeight - CoverBarClearance).coerceAtLeast(0.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = coverHeight)
                .focusGroup()
                .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        // Keyed on the film itself, not an index: the outgoing slide during
        // a fade still reads the film it started on, and if the lineup has
        // shrunk since (a title dropped by a refresh, pinned elsewhere) an
        // index into the new list could point past its end — a `MediaSet`
        // value stays valid to draw regardless of what the list looks like now.
        Crossfade(targetState = current, animationSpec = tween(CROSSFADE_MS), label = "tvHomeCover") { set ->
            TvCoverSlide(set = set, minHeight = coverHeight)
        }
        TvCoverActions(
            title = current.title,
            watchlisted = current.setId in watchlist,
            dotCount = films.size,
            current = films.indexOfFirst { it.setId == current.setId }.coerceAtLeast(0),
            onPlay = { onPlay(current) },
            onToggleWatchlist = { listed -> onToggleWatchlist(current.setId, listed) },
            onDetails = { onOpenTitle(current.setId) },
            onFocusDot = { index -> currentId = films[index].setId },
            watchNowFocus = arrivalFocus,
            upExit = upExit,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
