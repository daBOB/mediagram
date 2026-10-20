package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.JoinUiState
import catalog.profile.WAITING_FOR_HOUSEHOLD
import designsystem.Spacing
import model.HouseholdWords
import ui.chrome.CatalogueTextButton
import ui.settings.QuietPill

/**
 * Join a household — the web's `household-join.js`: which household's
 * profiles this device shows, one button for each live one but the one it
 * shows now that a PIN can open, or a line saying there is none. A press
 * asks that household's admin's PIN or the owner's; the gate draws that
 * prompt ([ProfileGate]), so it outlives a page that leaves once the join
 * takes. Read again as it is shown: a household made on another device
 * changes nobody here, so nothing else would read it. The gate's, the
 * picker's in place of a first profile, and Switch household's alike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JoinHousehold(
    join: JoinHouseholdViewModel,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val state by join.state.collectAsStateWithLifecycle()
    LaunchedEffect(join) { join.reread() }
    Column(modifier) {
        Text(HouseholdWords.JOIN_A_HOUSEHOLD, style = titleStyle)
        Text(
            HouseholdWords.JOIN_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
        if (state.read && state.households.isEmpty()) {
            Text(HouseholdWords.NONE_TO_JOIN, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.medium))
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.padding(top = Spacing.medium),
            ) {
                state.households.forEach { household -> QuietPill(household.name, onClick = { join.pick(household.id) }) }
            }
        }
    }
}

/**
 * The gate for a device that has joined no household: whose profiles to
 * show comes before who is watching, so this stands alone under the picker's
 * heading, as the web's picker draws it. Back leaves the app, as on a first run.
 */
@Composable
internal fun JoinHouseholdRoute(join: JoinHouseholdViewModel) {
    Column(Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(HEADING, style = MaterialTheme.typography.headlineSmall)
        JoinHousehold(join, Modifier.padding(top = Spacing.medium))
    }
}

/**
 * What stands where the picker's first profile would, or null for the form
 * itself. Nothing is made before the households are read: with grown-ups
 * elsewhere the core refuses a first profile, and the device is offered the
 * other households to join instead, as the web's `joinElsewhere`.
 */
internal fun insteadOfFirstProfile(
    join: JoinHouseholdViewModel,
    state: JoinUiState,
): (@Composable () -> Unit)? {
    if (state.read && !state.joinsElsewhere) return null
    return { if (state.read) JoinHousehold(join) else HouseholdsUnread(onRetry = join::reread) }
}

/** The households not read yet, so not whether there is anyone elsewhere: wait, or look again. */
@Composable
private fun HouseholdsUnread(onRetry: () -> Unit) {
    Text(WAITING_FOR_HOUSEHOLD, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium))
    CatalogueTextButton(onClick = onRetry) { Text("Try again") }
}
