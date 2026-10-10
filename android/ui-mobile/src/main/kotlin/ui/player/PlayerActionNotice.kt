package ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
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
        dismissAction = { CatalogueTextButton(onClick = onDismiss) { Text("Dismiss") } },
    ) { Text(notice) }
}
