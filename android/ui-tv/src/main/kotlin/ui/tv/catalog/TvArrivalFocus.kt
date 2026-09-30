package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Whether the wall, rows or list on screen take the remote the moment they
 * appear, as every one of them does by default. Turned off by a screen
 * whose way back lands somewhere they cannot see — the masthead's Search,
 * a genre link in a show's header — so that stop keeps the remote rather
 * than losing it to the first plate a moment later.
 */
internal val LocalTakesArrivalFocus = compositionLocalOf { true }

/**
 * [covered], except a return to `false` waits one frame — the fix for a
 * return from a pushed frame landing on the bar's own pill instead of the
 * plate that opened it, found on the box: the frame that removes the
 * pushed frame from the composition is also the frame Android notices that
 * frame's own focused view just detached, clears focus on the whole
 * `AndroidComposeView`, and re-grants it to the first focusable it finds —
 * the bar's pill, ahead of whatever the covered layer's own arrival effect
 * asked for a moment earlier in that same frame, which is what this races.
 * One frame later, that reset has already happened; an arrival request
 * made then is the last one standing rather than the one the reset undoes.
 *
 * Turning `true` (something is freshly covered) still happens at once — the
 * layer's own inertness must not lag a frame behind the frame it is meant
 * to protect against.
 */
@Composable
internal fun rememberArrivalReady(covered: Boolean): Boolean {
    var ready by remember { mutableStateOf(!covered) }
    LaunchedEffect(covered) {
        if (covered) {
            ready = false
        } else {
            withFrameNanos { }
            ready = true
        }
    }
    return ready
}
