package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.FIRST_PROFILE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.PICKER_NOTE
import catalog.profile.ProfileUiState
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvTextRow
import ui.tv.setup.TvLoadingIndicator

private const val HEADING = "Who's watching?"
private const val TRY_AGAIN = "Try again"
private const val STAY = "Stay as I am"

// The web reference (`style.css` `.who-card { max-width: 40rem; text-align:
// center }`) keeps the whole picker — heading, tiles and note alike — from
// running edge to edge and centres it; this is that same intent carried into
// dp for the note specifically, not a unit-exact rem-to-dp conversion.
private val NoteMaxWidth = 640.dp

/** The first tile's own tag — the one focused when the picker appears with nothing else to land on. */
internal const val TvProfilePickerFirstTileTag = "tv-profile-picker-first-tile"

/**
 * The television counterpart to `ui.profile.ProfilePickerScreen` and the
 * web's `profile-picker.js`: the same [ProfileUiState] and the same words,
 * in the web's order — a refusal, then the first profile on a device with no
 * grown-up or "Who runs this household?" while nobody does, the tiles in a
 * single row a D-pad moves across, Manage profiles once there is a grown-up,
 * and the honest note. The first profile's name is asked in place, as every
 * TV text question is. Renders nothing for [ProfileUiState.Chosen] — the
 * caller only shows this while there is something left to decide.
 *
 * [landing] is what the remote left the picker from, and comes back to;
 * [tiles] keeps the row's scroll while the picker is away, so a tile the
 * remote comes back to is still composed.
 */
@Composable
internal fun TvProfilePicker(
    state: ProfileUiState,
    onChoose: (String) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
    onClaim: (String) -> Unit = {},
    onCreateFirst: (String) -> Unit = {},
    onManage: () -> Unit = {},
    landing: TvPickerSpot? = null,
    tiles: LazyListState = rememberLazyListState(),
) {
    when (state) {
        ProfileUiState.Loading -> TvLoadingIndicator()
        is ProfileUiState.Picking -> TvPickerBody(state, TvPickerActions(onChoose, onStay, onRetry, onClaim, onCreateFirst, onManage), landing, tiles)
        is ProfileUiState.Chosen -> Unit
    }
}

private class TvPickerActions(
    val onChoose: (String) -> Unit,
    val onStay: () -> Unit,
    val onRetry: () -> Unit,
    val onClaim: (String) -> Unit,
    val onCreateFirst: (String) -> Unit,
    val onManage: () -> Unit,
)

@Composable
private fun TvPickerBody(
    state: ProfileUiState.Picking,
    actions: TvPickerActions,
    landing: TvPickerSpot?,
    tiles: LazyListState,
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TvAddProfileFlow(
            heading = FIRST_PROFILE,
            askLimit = false,
            onAdd = { name, _ ->
                naming = false
                actions.onCreateFirst(name)
            },
            onCancel = { naming = false },
        )
        return
    }

    val error = state.error
    val focus = focusSpot(state, landing)
    val requester = remember { FocusRequester() }
    // Again whenever what should hold the remote changes — the last tile
    // gone, a first profile made: whatever the remote was on may be gone.
    LaunchedEffect(focus) { requester.requestFocus() }
    fun at(spot: TvPickerSpot) = requester.takeIf { focus == spot }

    val side = Modifier.padding(horizontal = Overscan.horizontal)
    val tryAgain = @Composable { TvTextRow(TRY_AGAIN, onClick = actions.onRetry, modifier = side.padding(top = Spacing.small), focusRequester = at(TvPickerSpot.TryAgain)) }

    // Scrolls: the household question, the tiles, Manage, the note and
    // "Stay as I am" are taller together than a 540dp television, and the
    // remote walking down brings each into view.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(HEADING, style = TvTypeScale.title, modifier = side)
        if (error != null) {
            Text(error, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = side.padding(top = Spacing.small))
            tryAgain()
        }
        state.notice?.let { Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = side.padding(top = Spacing.small)) }
        // A failed load says nothing of whether a household exists, so
        // neither question is asked over one.
        if (error == null && state.needsFirstProfile) {
            TvFirstProfileRow(onStart = { naming = true }, focusRequester = at(TvPickerSpot.FirstProfile))
            // A household's first sync may still be on its way: look again
            // rather than make a first profile that would lose the role to it.
            if (state.profiles.isEmpty()) tryAgain()
        }
        if (error == null && state.needsAdmin) TvAdminQuestion(state.grownUps, actions.onClaim, focus, requester)
        // A LazyRow, not a plain Row: in a Row, enough fixed-width tiles to
        // run past the safe width squeeze a later one down to nothing rather
        // than scroll to it. contentPadding carries the Overscan margin
        // without clipping a focused edge tile's growth. The tiles narrow to
        // fit the safe width first, so a household's profiles are all in
        // view at once, as on the phone; only past the narrowest does the
        // row scroll.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = Spacing.large)) {
            val tileWidth = profileTileWidth(state.profiles.size, maxWidth - Overscan.horizontal * 2, Spacing.medium)
            LazyRow(
                state = tiles,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Overscan.horizontal),
                // Centred: with few enough profiles that the row does not
                // scroll, the tiles still read as one centred group rather
                // than pinned to the left edge.
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
            ) {
                itemsIndexed(items = state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                    TvProfileTile(
                        profile = profile,
                        onClick = { actions.onChoose(profile.id) },
                        focusRequester = at(TvPickerSpot.Tile(profile.id)),
                        tag = if (index == 0) TvProfilePickerFirstTileTag else "tv-profile-tile-${profile.id}",
                        width = tileWidth,
                    )
                }
            }
        }
        if (state.grownUps.isNotEmpty()) {
            TvTextRow(
                text = MANAGE_PROFILES,
                onClick = actions.onManage,
                focusRequester = at(TvPickerSpot.Manage),
                modifier = side.testTag(TvManageProfilesTag).padding(top = Spacing.medium),
            )
        }
        Text(
            PICKER_NOTE,
            style = TvTypeScale.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = NoteMaxWidth).then(side).padding(top = Spacing.large),
        )
        if (state.canStay) TvTextRow(text = STAY, onClick = actions.onStay, modifier = side.padding(top = Spacing.small))
    }
}
