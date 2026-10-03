package ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagram.android.core.designsystem.R
import designsystem.Eyebrow
import designsystem.LocalCatalogueTones
import designsystem.Radius
import designsystem.Spacing
import designsystem.StatusDot
import ui.chrome.Wordmark

/**
 * Settings/System's left pane on every width, and the whole screen in one
 * pane on compact. Narrower than the mockups' 320dp: they were drawn at
 * 1600dp, and a tablet held in the hand is closer to 1160dp wide, where
 * every dp the index keeps is one a section's columns cannot have.
 */
internal val SettingsIndexWidth = 280.dp

private val IconOf =
    mapOf(
        SettingsSection.TELEGRAM to R.drawable.core_designsystem_ic_settings_telegram,
        SettingsSection.STORAGE to R.drawable.core_designsystem_ic_settings_storage,
        SettingsSection.APPEARANCE to R.drawable.core_designsystem_ic_settings_appearance,
        SettingsSection.PROFILE to R.drawable.core_designsystem_ic_settings_profile,
        SettingsSection.SYSTEM to R.drawable.core_designsystem_ic_settings_system,
    )

/**
 * The index: back arrow and wordmark, the SETTINGS eyebrow, the five
 * section rows each with their own status, and — only on a width wide
 * enough to show it beside the page — the library's own tally underneath.
 */
@Composable
internal fun SettingsIndex(
    selected: SettingsSection?,
    statuses: Map<SettingsSection, IndexStatus>,
    tally: List<String>,
    onSelect: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = LocalCatalogueTones.current
    Column(
        modifier =
            modifier
                .background(tones.sidebar)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.medium, vertical = Spacing.extraLarge)
                .selectableGroup(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.extraLarge)) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to the library" }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Wordmark()
        }
        Text(
            text = "SETTINGS",
            style = Eyebrow,
            color = tones.quiet,
            modifier = Modifier.padding(start = Spacing.small, end = Spacing.small, bottom = Spacing.small),
        )
        for (section in SettingsSection.entries) {
            // The mockup's own "apart" gap: System sits a little away from
            // the other three, which read as one group about the account
            // and the device, and it as the one about the app itself.
            if (section == SettingsSection.SYSTEM) Spacer(Modifier.height(Spacing.small))
            SettingsIndexRow(
                section = section,
                selected = section == selected,
                status = statuses[section],
                icon = painterResource(IconOf.getValue(section)),
                onSelect = { onSelect(section) },
            )
        }
        if (tally.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = Spacing.small, end = Spacing.small, top = Spacing.large),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (line in tally) {
                    Text(text = line.uppercase(), style = MaterialTheme.typography.labelSmall, color = tones.quiet)
                }
            }
        }
    }
}

@Composable
private fun SettingsIndexRow(
    section: SettingsSection,
    selected: Boolean,
    status: IndexStatus?,
    icon: Painter,
    onSelect: () -> Unit,
) {
    val tones = LocalCatalogueTones.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(Radius.control))
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)) else Modifier)
                .selectable(selected = selected, onClick = onSelect, role = Role.Tab)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                text = section.title,
                // Geist, not Fraunces: the mockup's own `.row b` sets no
                // font-family of its own, so it inherits the body's Geist
                // rather than the wordmark's serif — `round2/tokens.css:73`.
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (it.held) {
                        StatusDot(MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        text = it.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = tones.quiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
