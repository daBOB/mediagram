package ui.tv.system

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import designsystem.Spacing
import ui.tv.TvTextRow
import ui.tv.setup.TvConfirmDialog
import ui.tv.setup.TvTextQuestion

/**
 * The phone's TMDB key screen as one of the television's text questions:
 * the same heading and explanation, whether a key is stored but never what
 * it is, and the key itself typed masked. The keyboard's own action key
 * saves it, as every text question on a television is answered.
 *
 * Where the phone clears a stored key by saving a blank, the television
 * ignores a blank answer and offers "Clear stored key" behind a
 * confirmation instead: on a remote the keyboard's action key is also how
 * a viewer who only came to look closes the keyboard, and that must not
 * throw the key away.
 */
@Composable
internal fun TvTmdbKeyScreen(
    hasKey: Boolean,
    onSave: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var askingClear by remember { mutableStateOf(false) }
    TvTextQuestion(
        heading = "Fetch poster artwork",
        explanation = "$TMDB_EXPLANATION ${if (hasKey) "A key is currently stored." else "No key is stored."}",
        label = "TMDB v3 key or v4 token",
        value = key,
        onValue = { key = it },
        onSubmit = {
            if (key.isNotBlank()) {
                onSave(key)
                key = ""
            }
        },
        secret = true,
        below = {
            if (hasKey) {
                TvTextRow(
                    text = "Clear stored key",
                    onClick = { askingClear = true },
                    modifier = Modifier.padding(top = Spacing.large),
                )
            }
        },
    )
    if (askingClear) {
        TvConfirmDialog(
            title = "Clear the TMDB key?",
            body = "Artwork and descriptions stop until a key is stored again.",
            confirmLabel = "Clear",
            confirm = {
                askingClear = false
                onSave("")
            },
            cancel = { askingClear = false },
        )
    }
}

private const val TMDB_EXPLANATION =
    "Optional. Create a free v3 API key or v4 read access token at " +
        "themoviedb.org and paste it here to let the catalog show artwork instead of " +
        "initials."
