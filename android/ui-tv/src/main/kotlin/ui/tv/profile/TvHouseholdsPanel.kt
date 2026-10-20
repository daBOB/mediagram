package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import designsystem.Overscan
import model.HouseholdWords

/**
 * The owner's Households on television — the web's `settings-households.js`
 * as screens the remote walks: every live household with who runs it and how
 * many are in it, then Create a household. OK on a household offers only what
 * the household rule allows, each a step of its own ([TvHouseholdStep]).
 *
 * Opened from the Owner row, it asks the owner's PIN first, and giving that
 * up leaves it; opened by the owner watching, it shows at once and asks the
 * PIN at the first change. The PIN is dropped when the panel is left, for
 * anything — another section, the app — and asked again on the way back.
 *
 * A change stays on the step that sent it until its answer: one that took
 * lands the remote on its household's row; a refusal is said on that step.
 * While one is out, presses send nothing more. Back walks out a row at a
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
) {
    val state by owner.state.collectAsStateWithLifecycle()
    val prompt by owner.pin.collectAsStateWithLifecycle()
    val joined by rememberUpdatedState(onJoined)
    var step by remember { mutableStateOf<TvHouseholdStep>(TvHouseholdStep.List()) }
    var sentFrom by remember { mutableStateOf<TvHouseholdStep?>(null) }
    LaunchedEffect(owner) {
        reread()
        owner.joined.collect { joined() }
    }
    LifecycleStartEffect(owner) {
        // The way in, and back from the background: the owner's PIN, unless the owner is watching.
        if (owner.state.value is HouseholdsOwnerUiState.Closed) owner.open()
        onStopOrDispose { owner.close() }
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
        Text(HouseholdWords.HOUSEHOLDS, style = WhoHeading, modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical))
        return
    }
    LaunchedEffect(part.answers) {
        if (sentFrom != null && part.notice == null) {
            step = sentFrom?.done() ?: step
            sentFrom = null
        }
    }
    // A household gone — deleted, here or elsewhere — takes its steps with it.
    val shown = step.takeIf { it.id == null || part.rows.any { row -> row.household.id == it.id } } ?: TvHouseholdStep.List()
    BackHandler { shown.back()?.let { step = it } ?: onDone() }
    TvHouseholdStepScreen(
        step = shown,
        part = part,
        notice = part.notice.takeIf { sentFrom == shown },
        owner = owner,
        go = { step = it },
        send = { change ->
            if (!part.sending) {
                sentFrom = shown
                change()
            }
        },
    )
}
