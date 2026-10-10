package ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import designsystem.Spacing
import ui.chrome.CatalogueTextButton
import ui.chrome.IndicationInk

/** A mark write the player could not confirm, said until dismissed. */
@Composable
internal fun ActionNoticeBar(
    notice: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    notice ?: return
    // The bar is the page's colours inverted, over the video where presses
    // tint white: Dismiss tints in the bar's own ink, which shows in either theme.
    IndicationInk(MaterialTheme.colorScheme.inverseOnSurface) {
        Snackbar(
            modifier = modifier.padding(Spacing.medium),
            dismissAction = { CatalogueTextButton(onClick = onDismiss) { Text("Dismiss") } },
        ) { Text(notice) }
    }
}
