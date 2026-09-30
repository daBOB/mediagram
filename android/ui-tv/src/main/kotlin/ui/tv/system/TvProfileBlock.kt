package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing
import model.Profile
import setup.ProfileSubtitleChoices
import setup.profileStatus
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * Settings › Profile, television-side: who is watching, then that profile's
 * default subtitle language as one radio row per choice — the phone's
 * `ProfileSettingsSection`, which says why the rows are disabled with no
 * profile chosen. The name block is [entryFocusRequester]'s target: it is
 * drawn whether or not a profile exists, so the section always has a stop
 * ready to be entered.
 */
@Composable
internal fun TvProfileBlock(
    profile: Profile?,
    subtitle: String,
    focusInContent: Boolean,
    entryFocusRequester: FocusRequester,
    onChooseSubtitle: (String) -> Unit,
) {
    LaunchedEffect(focusInContent) { if (focusInContent) entryFocusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(
            heading = "Profile",
            rows = listOf("Watching as" to profileStatus(profile)),
            modifier = Modifier.focusRequester(entryFocusRequester),
            focusable = true,
        )
        TvQuietLine(if (profile == null) "Choose a profile first." else "Subtitles, for every title this profile opens:")
        for ((value, label) in ProfileSubtitleChoices) {
            val chosen = value == subtitle
            TvTextRow(
                text = "${if (chosen) CHOSEN else NOT_CHOSEN}  $label",
                onClick = { onChooseSubtitle(value) },
                enabled = profile != null,
                modifier =
                    Modifier.semantics {
                        selected = chosen
                        role = Role.RadioButton
                    },
            )
        }
        if (profile != null) TvQuietLine("Forced subtitles still appear when a film switches language.")
    }
}
