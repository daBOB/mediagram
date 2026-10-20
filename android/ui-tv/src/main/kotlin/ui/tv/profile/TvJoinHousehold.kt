package ui.tv.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.JoinHouseholdViewModel
import designsystem.Overscan
import designsystem.Spacing
import model.HouseholdWords
import ui.tv.TvTextRow

/**
 * Join a household on television — the web's `household-join.js`: which
 * household's profiles this box shows, one row per household the remote
 * walks down, then that household's admin's PIN or the owner's on the pad, in
 * the list's place, one screen at a time as the picker's gate draws them;
 * with none a PIN here opens, a line says so. A refusal stays on the pad
 * with its reason; Back from it is back on the row that asked. The
 * households are read again as this shows, so one made on another device
 * since is offered too.
 *
 * [onJoined] hears each join — whether the profile chosen here still is — so
 * a panel can close. Asking who is watching again is the gate's: it outlives
 * any panel.
 */
@Composable
internal fun TvJoinHousehold(
    join: JoinHouseholdViewModel,
    onJoined: (kept: Boolean) -> Unit = {},
) {
    val state by join.state.collectAsStateWithLifecycle()
    val prompt by join.pin.collectAsStateWithLifecycle()
    val joined by rememberUpdatedState(onJoined)
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(join) {
        join.reread()
        join.joined.collect { joined(it) }
    }
    val asking = prompt
    if (asking != null) {
        TvPinPrompt(asking, onPin = join::enterPin, onCancel = join::cancelPin)
        return
    }
    val none = HouseholdWords.NONE_TO_JOIN.takeIf { state.read }
    TvChoicesScreen(HouseholdWords.JOIN_A_HOUSEHOLD, HouseholdWords.JOIN_NOTE, state.households.map { it.id to it.name }, landing = picked, none = none) { id ->
        picked = id
        join.pick(id)
    }
}

/**
 * A whole screen of choices the remote walks down, under [heading] and a
 * quiet [note], with [error] — why the last change did not take — beneath:
 * one keyed row per choice, given as its key and its words, the remote on
 * [landing] while it is offered, else on the first; with no choice, the
 * quiet line [none] in their place.
 */
@Composable
internal fun TvChoicesScreen(
    heading: String,
    note: String?,
    choices: List<Pair<String, String>>,
    landing: String?,
    error: String? = null,
    none: String? = null,
    onChoose: (String) -> Unit,
) {
    // One requester per choice for as long as it is shown, so no row's chain ever changes.
    val rows = remember { mutableMapOf<String, FocusRequester>() }
    // The row holding the remote, if any. A row stops holding it only by focus moving on while it is
    // still shown — to another row, or out to the chrome bar — so a row going is told from the remote leaving.
    val showing by rememberUpdatedState(choices)
    var holding by remember { mutableStateOf<String?>(null) }
    val land = (choices.find { it.first == landing } ?: choices.firstOrNull())?.first
    val lost = holding != null && choices.none { it.first == holding }
    // Once they arrive — a list not read yet has nothing to land on — and when the row holding the remote goes;
    // never for a change while the remote is elsewhere.
    LaunchedEffect(land != null, lost) { land?.let { rows[it]?.requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Text(heading, style = WhoHeading)
        note?.let { Text(it, style = WhoNote, modifier = Modifier.padding(top = Spacing.small)) }
        error?.let { Text(it, style = WhoNote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small)) }
        Spacer(Modifier.height(Spacing.small))
        if (choices.isEmpty()) none?.let { Text(it, style = WhoNote, modifier = Modifier.padding(top = Spacing.small)) }
        choices.forEach { (id, words) ->
            key(id) {
                val row = remember { FocusRequester() }.also { rows[id] = it }
                TvTextRow(words, onClick = { onChoose(id) }, focusRequester = row, modifier = Modifier.onFocusChanged { if (it.isFocused) holding = id else if (holding == id && showing.any { one -> one.first == id }) holding = null }.padding(top = Spacing.small))
            }
        }
    }
}
