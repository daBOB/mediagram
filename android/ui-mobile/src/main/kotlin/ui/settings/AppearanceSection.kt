package ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Accent
import designsystem.Appearance
import designsystem.Backdrop
import designsystem.LocalCatalogueTones
import designsystem.Motion
import designsystem.Palette
import designsystem.SectionHead
import designsystem.Spacing
import designsystem.ThemeChoice
import designsystem.catalogueIndication

/**
 * Settings › Appearance: theme, accent colour, and the web's own Artwork
 * setting — three questions, one column, the approved mockup's own
 * `round2/b-appearance.html`. Unlike the mockup's own "not yet on Android"
 * tag, Artwork is live here: [Backdrop] and
 * [designsystem.AppearanceViewModel.chooseBackdrop] already carry it.
 */
@Composable
internal fun AppearanceSection(
    appearance: Appearance,
    onChooseTheme: (ThemeChoice) -> Unit,
    onChooseAccent: (Accent) -> Unit,
    onChooseBackdrop: (Backdrop) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ThemeGroup(appearance.theme, onChooseTheme)
        AccentGroup(appearance.accent, onChooseAccent)
        ArtworkGroup(appearance.backdrop, onChooseBackdrop)
    }
}

@Composable
private fun ThemeGroup(
    selected: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = "Theme", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (choice in ThemeChoice.entries) {
                val isSelected = choice == selected
                Column(
                    modifier =
                        Modifier
                            .width(220.dp)
                            .selectable(selected = isSelected, interactionSource = null, indication = LABEL_PRESS, onClick = { onSelect(choice) }, role = Role.RadioButton)
                            .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                ) {
                    SwatchCard(selected = isSelected) {
                        Box(modifier = Modifier.fillMaxSize().background(themeSwatchBrush(choice)))
                    }
                    Text(text = choice.label, style = MaterialTheme.typography.bodyMedium)
                    Text(text = choice.note, style = MaterialTheme.typography.bodySmall, color = LocalCatalogueTones.current.quiet)
                }
            }
        }
    }
}

/** A flat preview of each theme's own ground — [Palette] roles, not the web's own decorative gradient, so no new hex joins this screen. */
private fun themeSwatchBrush(choice: ThemeChoice): Brush =
    when (choice) {
        ThemeChoice.DARK -> Brush.linearGradient(listOf(Palette.Page, Palette.Ground))
        ThemeChoice.LIGHT -> Brush.linearGradient(listOf(Palette.LightPage, Palette.LightGround))
        ThemeChoice.AUTO -> Brush.linearGradient(listOf(Palette.LightGround, Palette.LightGround, Palette.Ground, Palette.Ground))
    }

@Composable
private fun AccentGroup(
    selected: Accent,
    onSelect: (Accent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = "Accent colour", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            for (accent in Accent.entries) {
                val isSelected = accent == selected
                Column(
                    modifier =
                        Modifier
                            .selectable(selected = isSelected, interactionSource = null, indication = LABEL_PRESS, onClick = { onSelect(accent) }, role = Role.RadioButton)
                            .semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // `.dot { transition: box-shadow var(--hover) }` (`appearance.css:57-58`): the
                    // ring is in the ink and fades in, and the dot itself never shrinks.
                    val shown = animateFloatAsState(if (isSelected) 1f else 0f, animationSpec = Motion.hover(), label = "accent ring")
                    Box(
                        modifier =
                            Modifier
                                .size(ACCENT_DOT)
                                .selectionRing(
                                    shown,
                                    ring = MaterialTheme.colorScheme.onBackground,
                                    ground = MaterialTheme.colorScheme.background,
                                    radius = ACCENT_DOT / 2,
                                )
                                .clip(CircleShape)
                                .background(accent.dark),
                    )
                    Text(text = accent.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ArtworkGroup(
    selected: Backdrop,
    onSelect: (Backdrop) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = "Artwork", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (backdrop in Backdrop.entries) {
                val isSelected = backdrop == selected
                Column(
                    modifier =
                        Modifier
                            .width(220.dp)
                            .selectable(selected = isSelected, interactionSource = null, indication = LABEL_PRESS, onClick = { onSelect(backdrop) }, role = Role.RadioButton)
                            .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                ) {
                    SwatchCard(selected = isSelected) {
                        Box(modifier = Modifier.fillMaxSize().background(artworkSwatchBrush(backdrop)))
                    }
                    Text(text = backdrop.label, style = MaterialTheme.typography.bodyMedium)
                    Text(text = backdrop.note, style = MaterialTheme.typography.bodySmall, color = LocalCatalogueTones.current.quiet)
                }
            }
        }
    }
}

@Composable
private fun artworkSwatchBrush(backdrop: Backdrop): Brush {
    val accent = MaterialTheme.colorScheme.primary
    return when (backdrop) {
        Backdrop.DEFAULT -> Brush.linearGradient(listOf(Palette.Page, Palette.Sunk))
        Backdrop.BLURRED -> Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Palette.Sunk))
        Backdrop.ARTWORK -> Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Palette.Sunk))
        Backdrop.SOLID -> Brush.linearGradient(listOf(Palette.Sunk, Palette.Sunk))
    }
}

/** An accent dot's size; its ring is drawn inside it. */
private val ACCENT_DOT = 40.dp

/** Each option is a `<label>` around a radio (`appearance.css:38-58`), which `button:active` (`theme.css:167`) doesn't reach, so pressing one never shrinks it. */
private val LABEL_PRESS = catalogueIndication(pressScale = 1f)
