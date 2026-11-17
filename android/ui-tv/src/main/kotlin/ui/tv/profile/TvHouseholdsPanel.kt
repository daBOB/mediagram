package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.MANAGE_PROFILES
import designsystem.Overscan

/**
 * The owner's household actions on television, around the owner's view
 * ([list]) — the web's `householdHeader` and `createHouseholdBlock` as screens
 * the remote walks. [list] draws the view, the remote on the header its
 * landing names, and moves to a step: OK on a household's header offers only
 * what the household rule allows, each a step of its own ([TvHouseholdStep]).
 *
 * Opened after the owner's tile on Who's managing?, the owner's PIN is held
 * already; opened by the owner watching, it shows at once and asks the PIN at
 * the first change. Opened neither way, it asks the owner's PIN first, and
 * giving that up leaves it. The PIN is dropped when the panel is left, for
 * anything — another section, the app, Who's managing? again — and asked
 * again on the way back, unless the owner's view still shows
 * ([catalog.profile.OwnerPin]).
 *
 * A change stays on the step that sent it until its answer — a PIN pad given
 * up is none — and keeps what was typed: one that took lands the remote on
 * its household's row; a refusal is said on that step. While one is out,
 * presses send nothing more. Back walks out a row at a
 * time; from the list it is [onDone]. [onJoined] runs once Use on this
 * device has moved the box; asking who is watching again is the gate's.
 * [reread] reads the households again as the panel opens.
 */
@Composable
internal fun TvHouseholdsPanel(
    owner: HouseholdsOwnerViewModel,
    reread: () -> Unit,
    onDone: () -> Unit,
    onJoined: () -> Unit = {},
    list: @Composable (landing: String?, go: (TvHouseholdStep) -> Unit) -> Unit,
) {
    val state by owner.state.collectAsStateWithLifecycle()
    val prompt by owner.pin.collectAsStateWithLifecycle()
    val joined by rememberUpdatedState(onJoined)
    var step by remember { mutableStateOf<TvHouseholdStep>(TvHouseholdStep.List()) }
    var sentFrom by remember { mutableStateOf<TvHouseholdStep?>(null) }
    // The answers counted when it was sent: only a later count is its answer.
    var sentAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(owner) {
        reread()
        owner.joined.collect { joined() }
    }
    LifecycleStartEffect(owner) {
        // The way in, and back from the background: the owner's PIN, unless the owner is watching.
        if (owner.state.value is HouseholdsOwnerUiState.Closed) owner.open()
        onStopOrDispose {
            // close() counts the answers afresh, and an answer to what was out is dropped with it.
            owner.close()
            sentFrom = null
        }
    }
    val asking = prompt
    if (asking != null) {
        TvPinPrompt(asking, onPin = owner::enterPin) {
            owner.cancelPin()
            if (owner.state.value !is HouseholdsOwnerUiState.Open) onDone()
        }
        return
    }
    val part = state as? HouseholdsOwnerUiState.Open
    if (part == null) {
        // Not read yet, or the PIN not given: nothing the remote could be left holding.
        BackHandler(onBack = onDone)
        Text(MANAGE_PROFILES, style = WhoHeading, modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical))
        return
    }
    LaunchedEffect(part.answers) {
        val from = sentFrom
        if (from != null && part.answers > sentAt && part.notice == null) {
            step = from.done()
            sentFrom = null
        }
    }
    // A household gone — deleted, here or elsewhere — takes its steps with it.
    val shown = step.takeIf { it.id == null || part.rows.any { row -> row.household.id == it.id } } ?: TvHouseholdStep.List()
    BackHandler { shown.back()?.let { step = it } ?: onDone() }
    if (shown is TvHouseholdStep.List) return list(shown.row) { step = it }
    TvHouseholdStepScreen(
        step = shown,
        part = part,
        notice = part.notice.takeIf { sentFrom == shown },
        owner = owner,
        go = { step = it },
        send = { from, change ->
            // Read as pressed, not as last drawn: two presses in one frame send once.
            val now = owner.state.value as? HouseholdsOwnerUiState.Open
            if (now != null && !now.sending) {
                step = from
                sentFrom = from
                sentAt = now.answers
                change()
            }
        },
    )
}
