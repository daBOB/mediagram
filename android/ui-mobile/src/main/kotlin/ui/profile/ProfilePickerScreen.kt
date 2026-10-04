package ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.profile.ProfileUiState
import designsystem.Spacing
import model.Profile

private const val HEADING = "Who's watching?"
private const val NOTE =
    "Profiles keep your places and lists apart. They are not a login — " +
        "anyone who can reach this app can pick any of them."

/**
 * Ports the web's picker (`profile-picker.js`): choose. Adding and removing
 * happen in Manage profiles, behind a grown-up's PIN, so they are not here.
 * Renders nothing for [ProfileUiState.Chosen] — the caller only shows this
 * while there is something left to decide.
 */
@Composable
fun ProfilePickerScreen(
    state: ProfileUiState,
    onChoose: (String) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ProfileUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ProfileUiState.Picking -> {
            PickerBody(state, onChoose, onStay, onRetry)
        }

        is ProfileUiState.Chosen -> {
            Unit
        }
    }
}

@Composable
private fun PickerBody(
    state: ProfileUiState.Picking,
    onChoose: (String) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(HEADING, style = MaterialTheme.typography.headlineSmall)
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
        }
        // With nobody to choose, looking again is the one thing left to do.
        if (state.error != null || state.profiles.isEmpty()) {
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize().weight(1f).padding(top = Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = state.profiles, key = Profile::id) { profile ->
                ProfileTile(name = profile.name, kids = profile.kids, onClick = { onChoose(profile.id) })
            }
        }
        Text(
            text = NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.canStay) {
            TextButton(onClick = onStay, modifier = Modifier.padding(top = Spacing.small)) {
                Text("Stay as I am")
            }
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    kids: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Initial(name)
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.small))
        if (kids) {
            Text(
                "KIDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
