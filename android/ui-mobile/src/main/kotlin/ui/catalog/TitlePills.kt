package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import ui.chrome.CatalogueOutlinedButton
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable
import ui.chrome.CatalogueMenu
import ui.chrome.CatalogueMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing

/**
 * The row of pills under a title's spread: what starts it, "My List", and a
 * ⋯ menu for what a page has room for but should not lead with — a Compose
 * port of `title-spread.js`'s `playPill`/`moreMenu` and `list-toggle.js`.
 * Shared by the film and the series page, which differ only in what the
 * first pill says and does — a resume label and its own destination are the
 * caller's to work out ([catalog.seriesResumeFor], [data.ResumePoint]).
 *
 * [onToggleEditorsChoice] absent hides its own ⋯ item, the same as the
 * web's own `moreMenu` answering `null` for an empty item list — a kids
 * profile has nothing to put there yet. The menu itself still opens when
 * [preloadRemoveItem] alone has something to offer: a film's own Preload
 * is not a household mark, so a kids profile keeps it (see
 * `TitlePreload.kt`'s own doc — Android-only by decision, the web player
 * has no film preload).
 */
@Composable
internal fun TitlePills(
    playLabel: String?,
    onPlay: () -> Unit,
    watchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    editorsChoicePinned: Boolean,
    onToggleEditorsChoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
    preloadPill: (@Composable () -> Unit)? = null,
    preloadRemoveItem: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (playLabel != null) PlayPill(playLabel, onPlay)
        preloadPill?.invoke()
        CatalogueOutlinedButton(
            onClick = onToggleWatchlist,
            modifier =
                Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = if (watchlisted) "Remove from My List" else "Add to My List" },
        ) {
            Text(if (watchlisted) "✓ My List" else "+ My List")
        }
        if (onToggleEditorsChoice != null || preloadRemoveItem != null) {
            var expanded by remember { mutableStateOf(false) }
            CatalogueTextButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).semantics { contentDescription = "More" },
            ) { Text("⋯") }
            CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (onToggleEditorsChoice != null) {
                    CatalogueMenuItem(
                        text = { Text(if (editorsChoicePinned) "Remove as editor's choice" else "Make editor's choice") },
                        onClick = {
                            expanded = false
                            onToggleEditorsChoice()
                        },
                    )
                }
                preloadRemoveItem?.invoke { expanded = false }
            }
        }
    }
}
