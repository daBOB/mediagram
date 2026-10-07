package ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Accent
import designsystem.Appearance
import designsystem.Backdrop
import designsystem.LocalCatalogueTones
import designsystem.PageHead
import designsystem.ThemeChoice
import model.Profile
import setup.SettingsUiState
import ui.common.settings.SettingsSection
import ui.system.SystemScreen

// Margins sized to the screen a tablet actually has (≈1160dp), not the
// mockups' 1600dp canvas: the page head and the columns under it get the width.
private val ExpandedPagePadding = PaddingValues(start = 48.dp, top = 64.dp, end = 40.dp, bottom = 32.dp)
private val CompactPagePadding = PaddingValues(24.dp)
private val HeadGap = 56.dp

/**
 * A settings page: [designsystem.PageHead] then the one section — the
 * approved mockups' own `.page` (`round2/tokens.css:83-85`). Every
 * multi-column section (Telegram, Storage, System) decides its own columns
 * through [SettingsColumns]; Appearance has one.
 */
@Composable
internal fun SettingsPage(
    section: SettingsSection,
    settingsState: SettingsUiState,
    appearance: Appearance,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onChooseTheme: (ThemeChoice) -> Unit,
    onChooseAccent: (Accent) -> Unit,
    onChooseBackdrop: (Backdrop) -> Unit,
    profile: Profile?,
    profileSubtitle: String,
    onChooseProfileSubtitle: (String) -> Unit,
) {
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val tones = LocalCatalogueTones.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Keyed on section, not remembered bare: EXPANDED keeps this
                // same call site across a switch (only `section` changes), so
                // an unkeyed rememberScrollState() would carry the outgoing
                // section's offset into the incoming one — the same bug the
                // TV pane already fixed for its own scroll state.
                .verticalScroll(remember(section) { ScrollState(0) })
                .padding(if (expanded) ExpandedPagePadding else CompactPagePadding),
    ) {
        PageHead(
            title = section.title,
            eyebrow = section.pageEyebrow.uppercase(),
            titleColor = MaterialTheme.colorScheme.onBackground,
            eyebrowColor = tones.quiet,
            maxTitleSize = if (expanded) 112.sp else 72.sp,
        )
        Spacer(Modifier.height(HeadGap))
        when (section) {
            SettingsSection.TELEGRAM ->
                TelegramSection(
                    expanded = expanded,
                    state = settingsState,
                    onChangeLibrary = onChangeLibrary,
                    onChangeApplication = onChangeApplication,
                    onSignOut = onSignOut,
                    onLoadSessions = onLoadSessions,
                    onRevokeSession = onRevokeSession,
                )

            SettingsSection.STORAGE -> StorageSection(expanded = expanded)
            SettingsSection.APPEARANCE ->
                AppearanceSection(
                    appearance = appearance,
                    onChooseTheme = onChooseTheme,
                    onChooseAccent = onChooseAccent,
                    onChooseBackdrop = onChooseBackdrop,
                )

            SettingsSection.PROFILE -> ProfileSettingsSection(profile, profileSubtitle, onChooseProfileSubtitle)
            SettingsSection.SYSTEM -> SystemScreen(expanded = expanded)
        }
    }
}
