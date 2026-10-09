package ui.catalog

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The smallest a control on a title's page may be, so a thumb never has to aim for less. */
internal val MIN_TOUCH_TARGET = 48.dp

/** The solid pill that starts a title — `title-spread.js`'s `playPill`: Play, Resume, or Join. */
@Composable
internal fun PlayPill(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text("▶ $label")
    }
}
