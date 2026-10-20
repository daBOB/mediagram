package ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.LocalCatalogueTones
import designsystem.Radius
import designsystem.Spacing
import model.Profile
import model.SECTIONS
import model.SectionAccessWords
import model.turning

/**
 * Which sections [profile] is shown, on its own line under its row in Manage
 * profiles — the web's `sectionSwitches`: a quiet "Sections", then a switch
 * per section in nav order, on while it is shown, the group named for whose
 * it is. Turning one hands [onSet] the whole hidden list, keys this app does
 * not know kept as they came; the panel redraws from the core's answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionSwitches(
    profile: Profile,
    onSet: (List<String>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Spacing.small).semantics { contentDescription = "${SectionAccessWords.TITLE} for ${profile.name}" },
    ) {
        // The group's name already says it, as the web hides its caption from a screen reader.
        Text(
            SectionAccessWords.TITLE,
            style = MaterialTheme.typography.bodySmall,
            color = LocalCatalogueTones.current.quiet,
            modifier = Modifier.clearAndSetSemantics {},
        )
        SECTIONS.forEach { section ->
            SectionSwitch(section.label, shown = section.key !in profile.hiddenSections) { onSet(profile.hiddenSections.turning(section.key)) }
        }
    }
}

/**
 * The look the user chose: a section the profile sees is a quiet outlined
 * chip, as the pills beside it are; only a hidden one stands out — filled
 * in ink, its name struck through.
 */
@Composable
private fun SectionSwitch(
    label: String,
    shown: Boolean,
    onTurn: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(Radius.control)
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 40.dp)
                .clip(shape)
                .background(if (shown) ink.copy(alpha = 0.05f) else ink)
                .border(1.dp, if (shown) MaterialTheme.colorScheme.outlineVariant else ink, shape)
                .toggleable(value = shown, role = Role.Switch, onValueChange = { onTurn() })
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = if (shown) ink else MaterialTheme.colorScheme.background,
            textDecoration = if (shown) null else TextDecoration.LineThrough,
        )
    }
}
