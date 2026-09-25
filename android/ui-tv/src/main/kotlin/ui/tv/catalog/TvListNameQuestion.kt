package ui.tv.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.tv.setup.TvTextQuestion

/**
 * `window.prompt("Name for the list", …)` on a television — the twin of the
 * phone's `ListNameDialog`, for both "New list" and rename, asked as a
 * [TvTextQuestion] the remote is already sitting in.
 *
 * The phone's title is the only text its dialog shows, so it is this
 * question's one label, the way sign-in's field label is the only text on
 * its step. There is no Create or Rename button to travel to: the
 * keyboard's own action key answers, and a blank name is not an answer,
 * just as the phone keeps its confirm button disabled until there is one.
 * Back leaves without naming anything, as the phone's Cancel does.
 */
@Composable
internal fun TvListNameQuestion(
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    BackHandler(onBack = onDismiss)
    TvTextQuestion(
        heading = "",
        explanation = null,
        label = "Name for the list",
        value = name,
        onValue = { name = it },
        onSubmit = { if (name.isNotBlank()) onConfirm(name) },
    )
}
