package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.LocalCatalogueTones
import designsystem.SectionHead
import designsystem.Spacing
import model.Profile
import setup.ProfileSubtitleChoices
import setup.profileStatus

/**
 * Settings › Profile: the chosen profile's name and its default subtitle
 * language, the web Settings page's own Profile panel. The language row is
 * disabled until a profile is chosen, since the default belongs to one.
 */
@Composable
internal fun ProfileSettingsSection(
    profile: Profile?,
    subtitle: String,
    onChooseSubtitle: (String) -> Unit,
) {
    val quiet = LocalCatalogueTones.current.quiet
    Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Watching as", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
            Text(text = profileStatus(profile), style = MaterialTheme.typography.bodyLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(text = "Subtitles", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                for ((value, label) in ProfileSubtitleChoices) {
                    SettingsChip(
                        text = label,
                        selected = value == subtitle,
                        enabled = profile != null,
                        onClick = { onChooseSubtitle(value) },
                    )
                }
            }
            Text(text = profileSubtitleHint(profile), style = MaterialTheme.typography.bodySmall, color = quiet)
        }
    }
}

/** The web's own hint line under the row. */
internal fun profileSubtitleHint(profile: Profile?): String =
    if (profile == null) "Choose a profile first." else "Forced subtitles still appear when a film switches language."
