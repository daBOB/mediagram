package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Text
import designsystem.Accent
import designsystem.Backdrop
import designsystem.Spacing
import designsystem.TvStyle
import designsystem.TvType.SectionHead

/**
 * Settings › Appearance, television-side: the accent swatches the phone's
 * own `AppearanceSection` offers, then the same Artwork question the web
 * and the phone ask — how a hero treats its own picture — then the TV's own
 * question, Theme: mediagram, Apple TV style or Google TV style ([TvStyle]),
 * which the phone does not ask yet. Dark/Light/Auto is not asked here — a
 * deliberate difference from the phone, `ui.tv.TvTheme`'s own doc says why
 * (a television stays dark regardless of the choice, so the question would
 * have nothing to answer).
 *
 * [entryFocusRequester] lands on the first accent swatch — all three rows are
 * static lists, always ready the moment this section mounts, so there is no
 * loading state to wait on the way [TvCacheBudgetBlock]/[TvSystemSection]
 * do for their own first control.
 *
 * Square under mediagram, where [TvFocus] draws every television plate square;
 * a style that rounds its cards rounds these too.
 */
@Composable
internal fun TvAppearanceSection(
    accent: Accent,
    backdrop: Backdrop,
    style: TvStyle,
    focusInContent: Boolean,
    entryFocusRequester: FocusRequester,
    onSelectAccent: (Accent) -> Unit,
    onSelectBackdrop: (Backdrop) -> Unit,
    onSelectStyle: (TvStyle) -> Unit,
) {
    LaunchedEffect(focusInContent) { if (focusInContent) entryFocusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Accent colour", style = SectionHead)
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
            Text(text = "Artwork", style = SectionHead)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                for (entry in Backdrop.entries) {
                    // Shares the row rather than a fixed card width: the content
                    // pane beside the index is narrower than four fixed cards,
                    // and the last ones were squeezed to a sliver.
                    TvChoiceSwatch(
                        label = entry.label,
                        note = entry.note,
                        colour = artworkSwatchColor(entry),
                        isSelected = entry == backdrop,
                        onSelect = { onSelectBackdrop(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Theme", style = SectionHead)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                for (entry in TvStyle.entries) {
                    TvStyleSwatch(style = entry, isSelected = entry == style, onSelect = { onSelectStyle(entry) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
