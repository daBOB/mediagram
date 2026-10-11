package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import designsystem.Spacing
import designsystem.TvStyle
import designsystem.TvType
import designsystem.inStyle
import designsystem.tvSp
import ui.tv.TvFocus
import ui.tv.look
import ui.tv.rememberStableRequester

@Composable
internal fun TvAccentSwatch(
    accent: Accent,
    isSelected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val label = accent.name.lowercase().replaceFirstChar(Char::uppercase)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .size(SwatchSize)
                    .focusRequester(rememberStableRequester(focusRequester))
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
        Text(text = label, style = TvType.bodySmall.copy(fontSize = 12.tvSp))
    }
}

/**
 * A 16:9 choice with its label and note under it — an Artwork mode or a TV
 * style. [colour] fills it; [face], if any, is drawn on it (a style's own
 * "Aa", so the three themes differ at a glance on a dark ground).
 */
@Composable
internal fun TvChoiceSwatch(
    label: String,
    note: String,
    colour: Color,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    face: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall), modifier = modifier) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .semantics {
                        contentDescription = label
                        role = Role.RadioButton
                        selected = isSelected
                    },
            shape = TvFocus.surfaceShape(),
            colors = ClickableSurfaceDefaults.colors(containerColor = colour),
            scale = TvFocus.surfaceScale(),
            border = TvFocus.surfaceBorder(selected = isSelected),
            glow = TvFocus.surfaceGlow(),
        ) {
            if (face != null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { face() }
        }
        Text(text = label, style = TvType.labelLarge)
        Text(text = note, style = TvType.bodySmall.copy(fontSize = 12.tvSp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A TV style's own ground with "Aa" in its own face and ink — the swatch Settings shows for it. */
@Composable
internal fun TvStyleSwatch(
    style: TvStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neutrals = style.look.neutrals
    TvChoiceSwatch(
        label = style.label,
        note = style.note,
        colour = neutrals.ground,
        isSelected = isSelected,
        onSelect = onSelect,
        modifier = modifier,
        face = { Text(text = "Aa", style = TvType.Base.titleLarge.inStyle(style).copy(fontSize = 28.tvSp), color = neutrals.text) },
    )
}

/** A flat preview of each mode's own weight — [Palette] roles and the live accent, not the web's own decorative gradient, so no new hex joins this screen. */
@Composable
internal fun artworkSwatchColor(backdrop: Backdrop): Color {
    val accent = MaterialTheme.colorScheme.primary
    return when (backdrop) {
        Backdrop.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
        Backdrop.BLURRED -> accent.copy(alpha = 0.55f)
        Backdrop.ARTWORK -> accent.copy(alpha = 0.7f)
        Backdrop.SOLID -> MaterialTheme.colorScheme.surface
    }
}

private val SwatchSize = 48.dp
