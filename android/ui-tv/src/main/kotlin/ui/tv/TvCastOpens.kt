package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import player.CastReceiverViewModel
import ui.common.LibraryPositions

/**
 * Listens for casts while composed — collecting the receiver is what keeps
 * the TV answering — and opens each one it accepts through [at]
 * ([LibraryPositions.castPlayer]). Answers how many have opened, for the
 * player to be keyed on: a cast in place of a title playing then stops that
 * title and opens afresh, the very title playing included, which an
 * in-place switch would never reopen to take the cast.
 */
@Composable
internal fun rememberCastOpens(at: LibraryPositions): Int {
    val receiver: CastReceiverViewModel = hiltViewModel()
    val opening by receiver.opening.collectAsStateWithLifecycle()
    var opened by remember { mutableIntStateOf(0) }
    LaunchedEffect(opening) {
        val cast = opening ?: return@LaunchedEffect
        at.castPlayer(cast.set)
        opened++
        receiver.opened()
    }
    return opened
}

/**
 * A cast accepted while no profile is chosen, played over the picker; the
 * picker comes back when it is over or left. The catalogue is asked only
 * for the title's line and its run, as the library's own player asks it.
 */
@Composable
internal fun TvCastOverPicker(
    at: LibraryPositions,
    opened: Int,
) {
    val catalog: CatalogViewModel = hiltViewModel()
    val catalogState by catalog.state.collectAsStateWithLifecycle()
    key(opened) { TvPlayerBranch(at, catalogState, leave = at::pop, onCastOver = at::toCatalog) }
}
