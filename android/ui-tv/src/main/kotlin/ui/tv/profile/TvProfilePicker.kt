package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.ProfileUiState
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvTextRow
import ui.tv.setup.TvLoadingIndicator

private const val HEADING = "Who's watching?"
private const val NOTE =
    "Profiles keep your places and lists apart. They are not a login — " +
        "anyone who can reach this app can pick any of them."
private const val TRY_AGAIN = "Try again"
private const val STAY = "Stay as I am"
private const val REMOVE = "Remove a profile…"

// The web reference (`style.css` `.who-card { max-width: 40rem; text-align:
// center }`) keeps the whole picker — heading, tiles and note alike — from
// running edge to edge and centres it; this is that same intent carried into
// dp for the note specifically, not a unit-exact rem-to-dp conversion.
private val NoteMaxWidth = 640.dp

/** The first tile's own tag — the one always focused when the picker appears. */
internal const val TvProfilePickerFirstTileTag = "tv-profile-picker-first-tile"

/** The Add tile's own tag, used whenever it is not also the first tile. */
internal const val TvProfilePickerAddTileTag = "tv-profile-picker-add-tile"

/**
 * The television counterpart to `ui.profile.ProfilePickerScreen`: the same
 * [ProfileUiState] switch, the same "Who's watching?" wording and the same
 * add-a-profile fields — drawn as tiles in a single row a D-pad moves
 * across instead of a phone grid a finger taps. Remove, as on the phone,
 * lists every profile and asks before it takes one. Renders nothing for
 * [ProfileUiState.Chosen], exactly as the phone's own picker does — the
 * caller only shows this while there is something left to decide.
 */
@Composable
fun TvProfilePicker(
    state: ProfileUiState,
    onChoose: (String) -> Unit,
    onAdd: (String, Boolean) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
    onRemove: (String) -> Unit = {},
) {
    when (state) {
        ProfileUiState.Loading -> TvLoadingIndicator()
        is ProfileUiState.Picking -> TvPickerBody(state, onChoose, onAdd, onStay, onRetry, onRemove)
        is ProfileUiState.Chosen -> Unit
    }
}

@Composable
private fun TvPickerBody(
    state: ProfileUiState.Picking,
    onChoose: (String) -> Unit,
    onAdd: (String, Boolean) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    // Composing the add flow in place of the tile row, not over it: the two
    // never need to be visible together, and a `naming` overlay would still
    // have to answer what the tiles behind it do with the remote while it
    // is up. Cancelling out of it below re-enters this branch, which is
    // also what re-seeds first-tile focus for free.
    if (adding) {
        TvAddProfileFlow(
            onAdd = { name, kids ->
                adding = false
                onAdd(name, kids)
            },
            onCancel = { adding = false },
        )
        return
    }

    val firstFocusRequester = remember { FocusRequester() }
    // Again when the last profile is removed: "Remove a profile…" goes
    // with it, and the remote that was on it would rest on nothing.
    LaunchedEffect(state.profiles.isEmpty()) { firstFocusRequester.requestFocus() }

    val error = state.error
    // With nothing loaded and a reason why, "Try again" is the only useful
    // thing on screen — the Add tile still opens, but starting an account's
    // very first profile is not the answer to a load that just failed.
    val focusTryAgainFirst = error != null && state.profiles.isEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(HEADING, style = TvTypeScale.title, modifier = Modifier.padding(horizontal = Overscan.horizontal))
        if (error != null) {
            Text(
                text = error,
                style = TvTypeScale.body,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small),
            )
            TvTextRow(
                text = TRY_AGAIN,
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small),
                focusRequester = if (focusTryAgainFirst) firstFocusRequester else null,
            )
        }
        // A LazyRow, not a plain Row: fixed-width tiles in a Row that is
        // never given more space than the screen's own safe width run out
        // of room once there are enough profiles to add up past it — a
        // later tile is squeezed down to nothing rather than the row
        // scrolling to reach it. contentPadding carries the same
        // Overscan.horizontal margin a plain wrapping padding would, but
        // without clipping a focused edge tile's own growth the way that
        // wrapping padding does.
        //
        // The tiles are narrowed to fit that safe width first, so the
        // profiles a household actually has are all in view at once, the
        // way the phone's picker shows every one of them: at their full
        // width five tiles ran past the right edge of a 960dp television,
        // "New profile" cut off mid-word in the overscan. Only past what
        // fits at the narrowest tile does the row scroll.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = Spacing.large)) {
            val tileWidth = profileTileWidth(state.profiles.size + 1, maxWidth - Overscan.horizontal * 2, Spacing.medium)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Overscan.horizontal),
                // Centred, the same as the plain Row this replaces: with few
                // enough profiles that the row does not scroll, the tiles still
                // read as one centred group rather than pinned to the left edge.
                // Once there are enough to fill the row, centring has nothing
                // left to do and the row simply scrolls.
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
            ) {
                itemsIndexed(items = state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                    TvProfileTile(
                        profile = profile,
                        onClick = { onChoose(profile.id) },
                        focusRequester = if (!focusTryAgainFirst && index == 0) firstFocusRequester else null,
                        tag = if (index == 0) TvProfilePickerFirstTileTag else "tv-profile-tile-${profile.id}",
                        width = tileWidth,
                    )
                }
                item {
                    TvAddTile(
                        onClick = { adding = true },
                        focusRequester = if (!focusTryAgainFirst && state.profiles.isEmpty()) firstFocusRequester else null,
                        tag = if (state.profiles.isEmpty()) TvProfilePickerFirstTileTag else TvProfilePickerAddTileTag,
                        width = tileWidth,
                    )
                }
            }
        }
        Text(
            NOTE,
            style = TvTypeScale.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = NoteMaxWidth).padding(horizontal = Overscan.horizontal).padding(top = Spacing.large),
        )
        // Below the note and above "Stay as I am", where the phone lists
        // it, and only while there is someone to remove.
        if (state.profiles.isNotEmpty()) {
            TvTextRow(
                text = REMOVE,
                onClick = { removing = true },
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small),
            )
        }
        if (state.canStay) {
            TvTextRow(
                text = STAY,
                onClick = onStay,
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small),
            )
        }
    }

    if (removing) {
        TvRemoveProfileDialog(state.profiles, onRemove = onRemove, onDismiss = { removing = false })
    }
}
