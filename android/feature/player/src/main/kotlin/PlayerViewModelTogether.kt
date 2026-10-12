package player

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import data.CatalogRepository
import data.TogetherLive
import data.isTablet
import data.isTelevision
import data.orDefault
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import model.MediaSet
import model.forKidsProfile
import player.together.TogetherController
import player.together.ViewerPlayer
import player.together.ViewerRelay

/*
 * Watch together in the player (`docs/mlib-spec.md` §7b): the controller,
 * wired to this ViewModel's handle, catalog, profile and Up next, and the
 * player the viewer's controls hold.
 */

/**
 * Built once, as the ViewModel is. From then on it hears the viewer's moves
 * through [relay], where the screen's and the media session's [ViewerPlayer]s
 * report them.
 */
internal fun PlayerViewModel.togetherController(
    live: TogetherLive,
    relay: ViewerRelay,
    catalog: CatalogRepository,
    context: Context,
): TogetherController {
    // The last title looked up, kept: the drift check asks for the session's every
    // two seconds, and the catalog lists every set to answer one.
    var last: MediaSet? = null
    return TogetherController(
        live,
        handle,
        openTitle = upNextController::requestTitle,
        titleOf = { id ->
            val set = last?.takeIf { it.setId == id } ?: orDefault(null, "looking up $id") { catalog.mediaSet(id) }
            last = set ?: last
            set?.takeIf(::onThisProfile)
        },
        label = { labelOf(context) },
        scope = viewModelScope,
    ).also { relay.controller = it }
}

/** The ViewModel going away: this screen leaves, and the media session's moves no longer reach it. */
internal fun PlayerViewModel.closeTogether(relay: ViewerRelay) {
    togetherController.close()
    // A newer player's, if one took over first, is left alone.
    if (relay.controller === togetherController) relay.controller = null
}

/** Whether the chosen profile sees [set]: a kids profile sees only what its limit allows, as its catalog shows it. */
private fun PlayerViewModel.onThisProfile(set: MediaSet): Boolean {
    val kid = repository.chosenProfile.value?.takeIf { it.kids } ?: return true
    return forKidsProfile(listOf(set), repository.snapshot.value.kidsMarks, kid.kidsLimit).isNotEmpty()
}

/** What this screen is called in a session's members, beside the web player's `Browser`. */
private fun labelOf(context: Context): String =
    when {
        isTelevision(context) -> "TV"
        isTablet(context) -> "Tablet"
        else -> "Phone"
    }

/** The player the screen and its controls hold: every play, pause and seek through it is the viewer's own. */
internal fun PlayerViewModel.viewerPlayer(relay: ViewerRelay): StateFlow<Player?> =
    handle.player
        .map { raw -> raw?.let { ViewerPlayer(it, relay::report) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

/** The player screen started or stopped: out of a session, it listens for one only while started. */
fun PlayerViewModel.screenShown(shown: Boolean) = togetherController.screenShown(shown)

/** The Watch together button: start, join, or leave. */
fun PlayerViewModel.onTogetherButton() = togetherController.onButton()

/** Leave watch together, from the player's menu. */
fun PlayerViewModel.leaveTogether() = togetherController.leave()
