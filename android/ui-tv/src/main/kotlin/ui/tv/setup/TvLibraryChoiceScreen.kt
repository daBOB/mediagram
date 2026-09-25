package ui.tv.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import setup.LibraryOption
import setup.LibraryPrompt
import setup.libraryPromptFor
import ui.tv.TvTextRow

private const val HEADING = "Which library should this device read?"

private const val EXPLANATION =
    "These are the channels this account can see. " +
        "Choose the one you upload to."

/** The first row's own tag, for the androidTest set that proves it takes initial focus. */
internal const val TvLibraryFirstRowTag = "tv-library-row-first"

/**
 * The last setup step on television, mirroring `ui.setup.LibraryScreen`:
 * pick a library out of what the account can already see. A list of rows
 * rather than a URL and a key, exactly as it is on the phone — nothing here
 * is typed, so nothing here is harder to reach with a D-pad than with a
 * finger.
 *
 * Titles are all this screen is given, the same rule the phone's picker
 * follows: the handle that goes back on a choice means nothing outside the
 * core, so it never reaches a composable, a log line or a saved state
 * bundle here either.
 */
@Composable
fun TvLibraryChoiceScreen(
    choices: List<LibraryOption>?,
    error: String?,
    onChoose: (String) -> Unit,
    onLookAgain: () -> Unit,
) {
    when (val prompt = libraryPromptFor(choices, error)) {
        LibraryPrompt.Waiting -> TvLoadingIndicator()

        is LibraryPrompt.LookAgain ->
            LookAgainScreen(explanation = prompt.explanation, onLookAgain = onLookAgain)

        is LibraryPrompt.Choose ->
            ChooseScreen(choices = prompt.choices, error = error, onChoose = onChoose)
    }
}

@Composable
private fun LookAgainScreen(
    explanation: String,
    onLookAgain: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Heading()
        Explanation(explanation, isError = true)
        TvTextRow(
            text = "Look again",
            onClick = onLookAgain,
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
        )
    }
}

@Composable
private fun ChooseScreen(
    choices: List<LibraryOption>,
    error: String?,
    onChoose: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Heading()
        Explanation(EXPLANATION, isError = false)
        // Beside the list rather than instead of it: what the channel said
        // is why the last pick did not take, and picking again is exactly
        // what to do about it — the same reasoning `ui.setup.LibraryScreen`
        // follows for the same error.
        if (error != null) Explanation(error, isError = true)
        LibraryRows(choices, onChoose)
    }
}

@Composable
private fun Heading() {
    Text(text = HEADING, style = TvTypeScale.title)
}

@Composable
private fun Explanation(
    text: String,
    isError: Boolean,
) {
    Text(
        text = text,
        style = TvTypeScale.body,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/**
 * An account can be in hundreds of channels, and the order is the account's
 * own — whatever Telegram itself lists first is first here — so `LazyColumn`
 * only ever needs to focus the row that is already first, never search for
 * one further down the list to seed with focus.
 */
@Composable
private fun LibraryRows(
    choices: List<LibraryOption>,
    onChoose: (String) -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = Spacing.medium),
    ) {
        itemsIndexed(items = choices, key = { _, choice -> choice.handle }) { index, choice ->
            TvTextRow(
                text = choice.title,
                onClick = { onChoose(choice.handle) },
                focusRequester = if (index == 0) firstFocusRequester else null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(if (index == 0) TvLibraryFirstRowTag else "tv-library-row-${choice.handle}")
                        .padding(vertical = Spacing.small),
            )
        }
    }
}
