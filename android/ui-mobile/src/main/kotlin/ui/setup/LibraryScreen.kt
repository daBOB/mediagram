package ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import setup.LibraryOption
import setup.LibraryPrompt
import setup.libraryPromptFor

private const val HEADING = "Which library should this device read?"

private const val EXPLANATION =
    "These are the channels this account can see. " +
        "Choose the one you upload to."

/**
 * The last setup step: pick a library out of what the account can already
 * see. Nothing is typed, which is the whole point — a URL and a
 * 44-character key were a keyboard on a tablet and a D-pad on a
 * television, and this is a list.
 *
 * Titles are all this screen is given. The handle that goes back with a tap
 * means nothing outside the core, so a channel's identity never reaches a
 * composable, a log line or a saved state bundle.
 */
@Composable
fun LibraryScreen(
    choices: List<LibraryOption>?,
    error: String?,
    onChoose: (String) -> Unit,
    onLookAgain: () -> Unit,
) {
    when (val prompt = libraryPromptFor(choices, error)) {
        LibraryPrompt.Waiting -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        is LibraryPrompt.LookAgain -> {
            Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
                Heading()
                Explanation(prompt.explanation, isError = error != null)
                TextButton(onClick = onLookAgain, modifier = Modifier.padding(top = Spacing.small)) {
                    Text("Look again")
                }
            }
        }

        is LibraryPrompt.Choose -> {
            Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
                Heading()
                Explanation(EXPLANATION, isError = false)
                // Beside the list rather than instead of it: what the channel
                // said is why the last pick did not take, and picking again is
                // exactly what to do about it.
                if (error != null) Explanation(error, isError = true)
                LibraryList(prompt.choices, onChoose)
            }
        }
    }
}

@Composable
private fun Heading() {
    Text(text = HEADING, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun Explanation(
    text: String,
    isError: Boolean,
) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/**
 * Lazy because an account can be in hundreds of channels, and the order is
 * the account's own: whatever its Telegram app shows at the top is at the
 * top here, so the channel someone is setting this device up for is where
 * they expect it.
 */
@Composable
private fun LibraryList(
    choices: List<LibraryOption>,
    onChoose: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        items(items = choices, key = LibraryOption::handle) { choice ->
            Column {
                Text(
                    text = choice.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // The row is the control, not a button inside it: on
                            // a touch screen the whole line is the target, and
                            // the role is what tells a screen reader so.
                            .clickable(role = Role.Button) { onChoose(choice.handle) }
                            .padding(vertical = Spacing.medium),
                )
                HorizontalDivider()
            }
        }
    }
}
