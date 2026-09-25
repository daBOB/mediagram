package ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import designsystem.Spacing

/** A mark write the player could not confirm, said until dismissed. */
@Composable
internal fun ActionNoticeBar(
    notice: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    notice ?: return
    Snackbar(
        modifier = modifier.padding(Spacing.medium),
        dismissAction = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    ) { Text(notice) }
}
