package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.DONE
import catalog.profile.ManageUiState
import designsystem.Overscan
import designsystem.Spacing
import model.HouseholdWords
import ui.tv.TvTextRow

/** A tile's own tag on Who's managing?, for tests. */
internal fun tvManagerTileTag(id: String) = "tv-manager-tile-$id"

/**
 * Which of Who's managing?'s tiles was chosen last, and the row's scroll —
 * held by whatever outlives the PIN pad and the view a tile opens, so the
 * remote comes back to that tile from either.
 */
@Stable
internal class TvWhoSpot {
    var chosen by mutableStateOf<String?>(null)
    val tiles = LazyListState()
}

/**
 * Who's managing? on television — the web's `profile-manage-who.js`, the
 * tablet's `WhoIsManaging`: Manage profiles' one question before anyone
 * manages, asked as Who's watching? is, with its own tiles
 * ([TvProfileTileRow]). The grown-ups here, the owner first and labelled
 * Owner; on a device showing another household the owner's tile ends the row
 * ([ManageUiState.ChoosingActor.managers]). Kids manage nothing and have no
 * tile. A tile asks that person's PIN — the owner's for the owner — through
 * [onChoose]; a wrong one is said on the pad, and nothing opens. The notice
 * says why it asks again.
 *
 * The remote lands on [spot]'s tile, or the first. Done and Back are [onDone].
 */
@Composable
internal fun TvWhoIsManaging(
    state: ManageUiState.ChoosingActor,
    spot: TvWhoSpot,
    onChoose: (String) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val managers = state.managers
    val focus = (managers.find { it.id == spot.chosen } ?: managers.firstOrNull())?.id
    val tile = remember { FocusRequester() }
    val done = remember { FocusRequester() }
    // Again when the tile to land on goes: a grown-up removed on another device.
    LaunchedEffect(focus) { (if (focus == null) done else tile).requestFocus() }
    val side = Modifier.padding(horizontal = Overscan.horizontal)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(HouseholdWords.WHOS_MANAGING, style = WhoHeading, modifier = side.semantics { heading() })
        state.notice?.let { Text(it, style = WhoNote, color = MaterialTheme.colorScheme.error, modifier = side.padding(top = Spacing.small)) }
        TvProfileTileRow(
            profiles = managers,
            state = spot.tiles,
            focused = focus,
            requester = tile,
            tagOf = { _, one -> tvManagerTileTag(one.id) },
            onChoose = { id ->
                spot.chosen = id
                onChoose(id)
            },
            lineOf = { one -> HouseholdWords.OWNER.takeIf { one.id == state.owner?.id } },
        )
        TvTextRow(DONE, onClick = onDone, focusRequester = done, modifier = side.padding(top = Spacing.medium))
    }
}
