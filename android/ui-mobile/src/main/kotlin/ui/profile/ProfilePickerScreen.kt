package ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import catalog.profile.FIRST_PROFILE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.PICKER_NOTE
import catalog.profile.ProfileUiState
import catalog.profile.WHO_RUNS_THIS
import catalog.profile.kidsTag
import designsystem.Spacing
import model.Profile
import ui.settings.QuietPill

private const val HEADING = "Who's watching?"

/** The "Who runs this household?" block's tag, for tests: its names are the tiles' too. */
internal const val WhoRunsTag = "who-runs-this-household"

/** What the picker can ask for, as one bundle the gate answers from its view model. */
internal data class PickerActions(
    val onPick: (id: String) -> Unit,
    val onClaim: (id: String) -> Unit,
    val onCreateFirst: (name: String) -> Unit,
    val onManage: () -> Unit,
    val onStay: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The web's picker (`profile-picker.js`) and the three ways a household can
 * stand: no grown-up yet — make the first, which runs it; grown-ups with
 * nobody running it — "Who runs this household?" above the tiles; or an
 * admin. Manage profiles is offered once there is any grown-up who could
 * manage. A tile is pressed through [PickerActions.onPick]: a kid's opens at
 * once, a grown-up's asks its PIN ([ProfileGate] draws [PinDialog]). Renders
 * nothing for [ProfileUiState.Chosen].
 */
@Composable
internal fun ProfilePickerScreen(
    state: ProfileUiState,
    actions: PickerActions,
) {
    when (state) {
        ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ProfileUiState.Picking -> PickerBody(state, actions)
        is ProfileUiState.Chosen -> Unit
    }
}

@Composable
private fun PickerBody(
    state: ProfileUiState.Picking,
    actions: PickerActions,
) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(HEADING, style = MaterialTheme.typography.headlineSmall)
        listOfNotNull(state.error, state.notice).forEach { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.small).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // With nobody to choose, looking again is worth offering: a phone's
        // first sync may not have brought the household's names in yet, and
        // a first profile made meanwhile would lose the admin role to theirs.
        if (state.error != null || state.profiles.isEmpty()) {
            TextButton(onClick = actions.onRetry) { Text("Try again") }
        }
        // A load that just failed is answered by trying again, not by starting a household.
        when {
            state.error != null -> Unit
            state.needsFirstProfile -> FirstProfile(actions.onCreateFirst)
            state.needsAdmin -> HouseholdQuestion(state.grownUps, actions.onClaim)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize().weight(1f).padding(top = Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = state.profiles, key = Profile::id) { profile -> ProfileTile(profile, onClick = { actions.onPick(profile.id) }) }
        }
        if (state.grownUps.isNotEmpty()) {
            TextButton(onClick = actions.onManage) { Text(MANAGE_PROFILES) }
        }
        Text(
            text = PICKER_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
        if (state.canStay) {
            TextButton(onClick = actions.onStay, modifier = Modifier.padding(top = Spacing.small)) { Text("Stay as I am") }
        }
    }
}

/** No grown-up here yet — a new device, or one that only knows kids: the first one made runs the household. */
@Composable
private fun FirstProfile(onCreate: (String) -> Unit) {
    Text(FIRST_PROFILE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.small))
    NameForm("Create", onSubmit = onCreate)
}

/** Asked while grown-ups exist but nobody runs the household; only a grown-up can, each a PIN away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseholdQuestion(
    grownUps: List<Profile>,
    onClaim: (String) -> Unit,
) {
    Column(Modifier.testTag(WhoRunsTag)) {
        Text(WHO_RUNS_THIS, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.small)) {
            grownUps.forEach { profile -> QuietPill(profile.name, onClick = { onClaim(profile.id) }) }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Initial(profile.name)
        Text(profile.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.small))
        profile.kidsTag?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** The letter on a tile. */
@Composable
private fun Initial(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(initialOf(text), style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun initialOf(name: String): String =
    name
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "?" }
