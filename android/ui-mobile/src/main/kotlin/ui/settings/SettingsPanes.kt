package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.LocalCatalogueTones

/**
 * Two panes on EXPANDED width — the index beside the page, both always on
 * screen — one pane below it: the index alone, or the page alone with its
 * own way back to the index. [section] is `null` only in the second case:
 * EXPANDED always resolves it to [SettingsSection.TELEGRAM] so the page has
 * something to show, the same fallback the compact page never needs because
 * it is not drawn until a row picks one.
 *
 * Back rules (the mockup has no bar, so this owns them entirely):
 * - EXPANDED: Back leaves the frame; there is no narrower state to fall back to.
 * - Compact, index shown: Back leaves the frame.
 * - Compact, a section open [leavesFromSection] (opened directly, the
 *   System menu shortcut): Back leaves the frame — the index was never shown.
 * - Compact, a section open, not [leavesFromSection] (opened from the
 *   index): Back returns to the index.
 */
@Composable
internal fun SettingsPanes(
    section: SettingsSection?,
    statuses: Map<SettingsSection, IndexStatus>,
    tally: List<String>,
    leavesFromSection: Boolean,
    onSelectSection: (SettingsSection?) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (SettingsSection) -> Unit,
) {
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val tones = LocalCatalogueTones.current

    if (expanded) {
        BackHandler(onBack = onLeave)
        val shown = section ?: SettingsSection.TELEGRAM
        Row(modifier = modifier.fillMaxSize()) {
            SettingsIndex(
                selected = shown,
                statuses = statuses,
                tally = tally,
                onSelect = { onSelectSection(it) },
                onBack = onLeave,
                // requiredWidth, not width: the index's own content (an
                // inset-driven start padding on some devices) must never be
                // able to push this pane — and so the page beside it —
                // wider than the mockup's own 320dp, only ever clip within it.
                modifier = Modifier.requiredWidth(SettingsIndexWidth).fillMaxHeight(),
            )
            VerticalDivider(color = tones.ruleSoft)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                page(shown)
            }
        }
        return
    }

    if (section == null) {
        BackHandler(onBack = onLeave)
        SettingsIndex(
            selected = null,
            statuses = statuses,
            tally = emptyList(),
            onSelect = { onSelectSection(it) },
            onBack = onLeave,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        BackHandler(onBack = { if (leavesFromSection) onLeave() else onSelectSection(null) })
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            page(section)
        }
    }
}
