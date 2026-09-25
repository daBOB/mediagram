package ui.catalog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import designsystem.Spacing
import model.MediaSet
import uniffi.mediagram_core.TitleInfo

/**
 * What the film shelf needs from outside to offer the Featured reel: a way
 * to play a film, and the same title lookup the details screen uses for a
 * score and a tagline.
 */
internal class FilmShelfActions(
    val onPlay: (MediaSet) -> Unit,
    val titleInfo: suspend (String) -> TitleInfo?,
)

/**
 * The controls above a shelf that offers them — the web's heading controls:
 * where the paged Movies shelf is ("page 2 of 13"), Featured when there is a
 * film left to suggest (`movieControls` in `app.js`), and List · Grid.
 */
@Composable
internal fun ShelfBar(
    pageLine: String?,
    view: ShelfViewChoice,
    onFeatured: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
        pageLine?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        onFeatured?.let { TextButton(onClick = it) { Text("Featured") } }
        ShelfModeToggle(view.chosen, view.onChoose)
    }
}
