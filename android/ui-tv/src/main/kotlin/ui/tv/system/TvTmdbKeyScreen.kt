package ui.tv.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.tv.setup.TvTextQuestion

/**
 * The phone's TMDB key screen as one of the television's text questions:
 * the same heading and explanation, whether a key is stored but never what
 * it is, and the key itself typed masked. The keyboard's own action key
 * saves it, as every text question on a television is answered; saving a
 * blank clears a stored key, the phone's rule, without a trip through
 * Start over.
 */
@Composable
internal fun TvTmdbKeyScreen(
    hasKey: Boolean,
    onSave: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    TvTextQuestion(
        heading = "Fetch poster artwork",
        explanation = "$TMDB_EXPLANATION ${if (hasKey) "A key is currently stored." else "No key is stored."}",
        label = "TMDB v3 key or v4 token",
        value = key,
        onValue = { key = it },
        onSubmit = {
            onSave(key)
            key = ""
        },
        secret = true,
    )
}

private const val TMDB_EXPLANATION =
    "Optional. Create a free v3 API key or v4 read access token at " +
        "themoviedb.org and paste it here to let the catalog show artwork instead of " +
        "initials."
