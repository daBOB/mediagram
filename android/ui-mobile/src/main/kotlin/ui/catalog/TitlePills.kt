package ui.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import ui.chrome.CatalogueButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import designsystem.hoverMotion

/** The smallest a control here may be, so a thumb never has to aim for less. */
private val MIN_TOUCH_TARGET = 48.dp

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
        if (playLabel != null) {
            val play = remember { MutableInteractionSource() }
            CatalogueButton(onClick = onPlay, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), interactionSource = play) {
                // Two nodes so the triangle can move on its own, still read as the one label it was.
                Row(
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("▶ $playLabel") },
                    // `.pill { gap: 10px }` (`title-page.css:9`).
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // `.pill:hover .pill-play` moves 2px over 520ms (`title-page.css:26-27`); a press nudges it too.
                    Text("▶", modifier = Modifier.hoverMotion(play, durationMs = 520, nudge = 2.dp))
                    Text(playLabel)
                }
            }
        }
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
