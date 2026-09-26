package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import designsystem.Spacing

private val FACT_LABEL_WIDTH = 96.dp

/**
 * Label–value rows, as a magazine prints the facts box beside a feature —
 * a Compose port of `title-spread.js`'s own `factSheet`. A row whose value
 * is `null` is left out entirely rather than shown blank, the same rule the
 * web's own version follows.
 */
@Composable
internal fun FactSheet(
    rows: List<Pair<String, (@Composable () -> Unit)?>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for ((label, value) in rows) {
            if (value == null) continue
            Row(modifier = Modifier.padding(vertical = Spacing.extraSmall)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(FACT_LABEL_WIDTH),
                )
                Column { value() }
            }
        }
    }
}

/** A fact whose value is a plain sentence, or no row at all when there is none to print. */
internal fun textFact(value: String?): (@Composable () -> Unit)? =
    value?.let { text -> { Text(text, style = MaterialTheme.typography.bodyMedium) } }

/** A fact whose value links onward — the franchise a film is "Part of". */
internal fun linkFact(
    label: String,
    onOpen: () -> Unit,
): @Composable () -> Unit =
    {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
        )
    }

/** The genres row, as the same links a film's own header already draws — `null` when there are none to name. */
internal fun genreFact(
    names: List<String>,
    onOpen: (String) -> Unit,
): (@Composable () -> Unit)? = names.takeIf { it.isNotEmpty() }?.let { { GenreLinks(it, onOpen) } }
