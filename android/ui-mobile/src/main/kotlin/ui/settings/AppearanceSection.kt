package ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Accent
import designsystem.Appearance
import designsystem.Palette
import designsystem.Spacing
import designsystem.ThemeChoice

/**
 * Settings › Appearance, phone-side: theme cards then accent dots, the
 * same two questions and the same options `lib/catalog/settings-page.js`
 * asks on the web. Only the web's third question — artwork mode — is not
 * ported (user decision 2026-09-26, phase 8).
 */
@Composable
internal fun AppearanceSection(
    appearance: Appearance,
    onChooseTheme: (ThemeChoice) -> Unit,
    onChooseAccent: (Accent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
        ThemeChoiceRow(selected = appearance.theme, onSelect = onChooseTheme)
        AccentRow(selected = appearance.accent, onSelect = onChooseAccent)
    }
}

/** [ThemeChoice] paired with the web's own label, note and swatch colour (`lib/catalog/settings-page.js`'s `THEMES`). */
private val ThemeCards =
    listOf(
        ThemeCard(ThemeChoice.DARK, "Dark", "Cinematic and focused", Palette.Ground),
        ThemeCard(ThemeChoice.LIGHT, "Light", "Clean and bright", Palette.LightGround),
        // Auto has no one swatch colour of its own on the web either — its card is a
        // split of both, which SplitSwatch below draws rather than a third solid tone.
        ThemeCard(ThemeChoice.AUTO, "Auto", "Follows your device", null),
    )

private data class ThemeCard(val choice: ThemeChoice, val label: String, val note: String, val swatch: Color?)

@Composable
private fun ThemeChoiceRow(
    selected: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(text = "Theme", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            for (card in ThemeCards) {
                val isSelected = card.choice == selected
                Column(
                    modifier =
                        Modifier
                            .selectable(selected = isSelected, onClick = { onSelect(card.choice) }, role = Role.RadioButton)
                            .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                ) {
                    if (card.swatch != null) {
                        Box(modifier = Modifier.size(width = SwatchWidth, height = SwatchHeight).background(card.swatch))
                    } else {
                        Row(modifier = Modifier.size(width = SwatchWidth, height = SwatchHeight)) {
                            Box(modifier = Modifier.size(width = SwatchWidth / 2, height = SwatchHeight).background(Palette.LightGround))
                            Box(modifier = Modifier.size(width = SwatchWidth / 2, height = SwatchHeight).background(Palette.Ground))
                        }
                    }
                    RadioButton(selected = isSelected, onClick = null)
                    Text(text = card.label, style = MaterialTheme.typography.bodyMedium)
                    Text(text = card.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AccentRow(
    selected: Accent,
    onSelect: (Accent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(text = "Accent colour", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            for (accent in Accent.entries) {
                AccentDot(
                    color = accent.dark,
                    label = accent.name.lowercase().replaceFirstChar(Char::uppercase),
                    selected = accent == selected,
                    onSelect = { onSelect(accent) },
                )
            }
        }
    }
}

@Composable
private fun AccentDot(
    color: Color,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Box(modifier = Modifier.size(DotSize).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

private val DotSize = 34.dp
private val SwatchWidth = 96.dp
private val SwatchHeight = 54.dp
