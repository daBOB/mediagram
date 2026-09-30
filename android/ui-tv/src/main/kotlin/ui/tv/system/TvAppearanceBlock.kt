package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Accent
import designsystem.Backdrop
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * Settings › Appearance, television-side: the accent swatches the phone's
 * own `AppearanceSection` offers, then the same Artwork question the web
 * and the phone ask — how a hero treats its own picture. Theme (Dark/Light/
 * Auto) is not asked here — a deliberate difference from the phone,
 * `ui.tv.TvTheme`'s own doc says why (a television stays dark regardless of
 * the choice, so the question would have nothing to answer).
 *
 * [entryFocusRequester] lands on the first accent swatch — both rows are
 * static lists, always ready the moment this section mounts, so there is no
 * loading state to wait on the way [TvCacheBudgetBlock]/[TvSystemContent]
 * do for their own first control.
 *
 * Square, not the web's and the phone's rounded swatches: [TvFocus] draws
 * every television plate with the same square corner, and a swatch that
 * bent that rule would be a second shape for the remote to learn.
 */
@Composable
internal fun TvAppearanceBlock(
    accent: Accent,
    backdrop: Backdrop,
    focusInContent: Boolean,
    entryFocusRequester: FocusRequester,
    onSelectAccent: (Accent) -> Unit,
    onSelectBackdrop: (Backdrop) -> Unit,
) {
    LaunchedEffect(focusInContent) { if (focusInContent) entryFocusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Accent colour", style = TvTypeScale.title)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                Accent.entries.forEachIndexed { index, entry ->
                    TvAccentSwatch(
                        accent = entry,
                        isSelected = entry == accent,
                        onSelect = { onSelectAccent(entry) },
                        focusRequester = if (index == 0) entryFocusRequester else null,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Artwork", style = TvTypeScale.title)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                for (entry in Backdrop.entries) {
                    // Shares the row rather than a fixed card width: the content
                    // pane beside the index is narrower than four fixed cards,
                    // and the last ones were squeezed to a sliver.
                    TvArtworkSwatch(
                        backdrop = entry,
                        isSelected = entry == backdrop,
                        onSelect = { onSelectBackdrop(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvAccentSwatch(
    accent: Accent,
    isSelected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val label = accent.name.lowercase().replaceFirstChar(Char::uppercase)
    // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
    val ownRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .size(SwatchSize)
                    .focusRequester(focusRequester ?: ownRequester)
                    .semantics {
                        contentDescription = label
                        role = Role.RadioButton
                        selected = isSelected
                    },
            shape = TvFocus.surfaceShape(),
            colors = ClickableSurfaceDefaults.colors(containerColor = accent.dark),
            scale = TvFocus.surfaceScale(),
            border = TvFocus.surfaceBorder(selected = isSelected),
            glow = TvFocus.surfaceGlow(),
        ) {}
        Text(text = label, style = TvTypeScale.body)
    }
}

@Composable
private fun TvArtworkSwatch(
    backdrop: Backdrop,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall), modifier = modifier) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .semantics {
                        contentDescription = backdrop.label
                        role = Role.RadioButton
                        selected = isSelected
                    },
            shape = TvFocus.surfaceShape(),
            colors = ClickableSurfaceDefaults.colors(containerColor = artworkSwatchColor(backdrop)),
            scale = TvFocus.surfaceScale(),
            border = TvFocus.surfaceBorder(selected = isSelected),
            glow = TvFocus.surfaceGlow(),
        ) {}
        Text(text = backdrop.label, style = TvTypeScale.body)
        Text(text = backdrop.note, style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A flat preview of each mode's own weight — [Palette] roles and the live accent, not the web's own decorative gradient, so no new hex joins this screen. */
@Composable
private fun artworkSwatchColor(backdrop: Backdrop): Color {
    val accent = MaterialTheme.colorScheme.primary
    return when (backdrop) {
        Backdrop.DEFAULT -> Palette.Sunk
        Backdrop.BLURRED -> accent.copy(alpha = 0.55f)
        Backdrop.ARTWORK -> accent.copy(alpha = 0.7f)
        Backdrop.SOLID -> Palette.Page
    }
}

private val SwatchSize = 48.dp
