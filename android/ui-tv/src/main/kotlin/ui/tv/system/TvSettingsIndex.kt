package ui.tv.system

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mediagram.android.core.designsystem.R
import designsystem.Eyebrow
import designsystem.LocalCatalogueTones
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.settings.IndexStatus
import ui.settings.SettingsSection
import ui.tv.TvFocus

/** The index pane's own width on a television — narrower than the phone's 320dp EXPANDED pane; there is no wordmark or tally competing with it here. */
internal val TvSettingsIndexWidth = 260.dp

private val IconOf =
    mapOf(
        SettingsSection.TELEGRAM to R.drawable.core_designsystem_ic_settings_telegram,
        SettingsSection.STORAGE to R.drawable.core_designsystem_ic_settings_storage,
        SettingsSection.APPEARANCE to R.drawable.core_designsystem_ic_settings_appearance,
        SettingsSection.SYSTEM to R.drawable.core_designsystem_ic_settings_system,
    )

/**
 * Settings' left pane on a television: the SETTINGS eyebrow and the four
 * section rows, each with its own status — the phone index's own content
 * (`ui.settings.SettingsIndex`), without its wordmark or back arrow (the
 * remote's own Back already does that job) or the library tally
 * (unreadable at three metres, the same reason the web hides it on a
 * narrow window).
 *
 * A row gaining focus — the remote moving along the column — only calls
 * [onFocusSection]: the section shown beside it follows, but nothing is
 * entered yet. Pressing the row (Right or OK) calls [onEnterSection] too,
 * which is what actually steps the remote into that section's own first
 * control.
 */
@Composable
internal fun TvSettingsIndex(
    selected: SettingsSection,
    statuses: Map<SettingsSection, IndexStatus>,
    rowRequesters: Map<SettingsSection, FocusRequester>,
    onFocusSection: (SettingsSection) -> Unit,
    onEnterSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = LocalCatalogueTones.current
    Column(
        modifier =
            modifier
                .background(tones.sidebar)
                .padding(horizontal = Spacing.medium, vertical = Spacing.extraLarge)
                .selectableGroup(),
    ) {
        Text(
            text = "SETTINGS",
            style = Eyebrow.copy(fontSize = TvTypeScale.eyebrow),
            color = tones.quiet,
            modifier = Modifier.padding(start = Spacing.small, bottom = Spacing.large),
        )
        for (section in SettingsSection.entries) {
            // The mockup's own "apart" gap: System reads as its own group,
            // away from the account/device questions above it.
            if (section == SettingsSection.SYSTEM) Spacer(Modifier.height(Spacing.medium))
            TvSettingsIndexRow(
                section = section,
                selected = section == selected,
                status = statuses[section],
                icon = painterResource(IconOf.getValue(section)),
                focusRequester = rowRequesters.getValue(section),
                onFocusSection = onFocusSection,
                onEnterSection = onEnterSection,
            )
        }
    }
}

@Composable
private fun TvSettingsIndexRow(
    section: SettingsSection,
    selected: Boolean,
    status: IndexStatus?,
    icon: Painter,
    focusRequester: FocusRequester,
    onFocusSection: (SettingsSection) -> Unit,
    onEnterSection: (SettingsSection) -> Unit,
) {
    val tones = LocalCatalogueTones.current
    var focused by remember { mutableStateOf(false) }
    val baseColor = if (selected) MaterialTheme.colorScheme.onBackground else tones.quiet
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) onFocusSection(section)
                }
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)) else Modifier)
                .selectable(selected = selected, role = Role.Tab, onClick = { onEnterSection(section) })
                .padding(horizontal = Spacing.small, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (focused) Palette.Imprint else baseColor),
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(text = section.title, style = TvFocus.textStyle(TvTypeScale.body.copy(color = baseColor), focused))
            status?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (it.held) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    }
                    Text(
                        text = it.text,
                        style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
                        color = tones.quiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
