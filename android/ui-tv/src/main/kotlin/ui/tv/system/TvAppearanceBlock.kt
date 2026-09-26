package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Accent
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * Settings' accent picker, television-side: the seven [Accent] swatches the
 * phone's own `AppearanceSection` offers, focusable with the remote. Theme
 * (Dark/Light/Auto) is not asked here — a deliberate difference from the
 * phone, `ui.tv.TvTheme`'s own doc says why (a television stays dark
 * regardless of the choice, so the question would have nothing to answer).
 *
 * Square, not the web's and the phone's circular dots: [TvFocus] draws
 * every television plate with the same square corner, and a swatch that
 * bent that rule would be a second shape for the remote to learn.
 */
@Composable
internal fun TvAppearanceBlock(
    selected: Accent,
    onSelect: (Accent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(text = "Appearance", style = TvTypeScale.title)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (accent in Accent.entries) {
                TvAccentSwatch(accent = accent, isSelected = accent == selected, onSelect = { onSelect(accent) })
            }
        }
    }
}

@Composable
private fun TvAccentSwatch(
    accent: Accent,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val label = accent.name.lowercase().replaceFirstChar(Char::uppercase)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .size(SwatchSize)
                    .semantics {
                        contentDescription = label
                        role = Role.RadioButton
                        selected = isSelected
                    },
            shape = TvFocus.surfaceShape(),
            colors = ClickableSurfaceDefaults.colors(containerColor = accent.dark),
            scale = TvFocus.surfaceScale(),
            border = TvFocus.surfaceBorder(),
            glow = TvFocus.surfaceGlow(),
        ) {}
        Text(text = label, style = TvTypeScale.body)
    }
}

private val SwatchSize = 48.dp
