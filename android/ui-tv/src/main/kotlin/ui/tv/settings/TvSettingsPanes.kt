package ui.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import designsystem.Eyebrow
import designsystem.LocalCatalogueTones
import designsystem.Overscan
import designsystem.PageHead
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.common.settings.IndexStatus
import ui.common.settings.SettingsSection
import ui.tv.catalog.TvPage

/**
 * Settings on a television: the index beside the page, both on screen at
 * once — the phone's own EXPANDED pane, at ten-foot sizes. There is no
 * narrower fallback to fold into: a television is one width, so this is
 * the only layout Settings ever draws here.
 *
 * [focusInContent] is owned by the hub (`TvSettingsScreen`), not this
 * composable: a library-choice or an address panel takes over the whole
 * screen while it is open, unmounting this pane entirely, and Back closing
 * that panel has to come back to whichever side — the index, or the
 * control that opened it — held the remote before.
 *
 * A row's focus alone ([SettingsSection] selection) only swaps which
 * section shows; pressing the row (Right or OK) is what steps the remote
 * into it — [rowRequesters] carries the first back to the index on Back or
 * on a Left press from any control in the section shown, and [content]'s
 * own `FocusRequester` is where a Right press or an OK on the selected row
 * lands, once the shown section says its own first control is ready.
 */
@Composable
internal fun TvSettingsPanes(
    section: SettingsSection,
    statuses: Map<SettingsSection, IndexStatus>,
    focusInContent: Boolean,
    onSelectSection: (SettingsSection) -> Unit,
    onFocusInContentChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsSection, Boolean, FocusRequester) -> Unit,
) {
    val tones = LocalCatalogueTones.current
    val rowRequesters = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val entryRequester = remember { FocusRequester() }

    // Only when arriving with nothing already entered: a panel just closed
    // with focusInContent already true leaves the landing to the shown
    // section's own effect below, which knows which of its own controls —
    // not necessarily its first — should take the remote back.
    LaunchedEffect(Unit) { if (!focusInContent) rowRequesters.getValue(section).requestFocus() }

    BackHandler(enabled = focusInContent) {
        onFocusInContentChange(false)
        rowRequesters.getValue(section).requestFocus()
    }

    Row(modifier = modifier.fillMaxSize()) {
        TvSettingsIndex(
            selected = section,
            statuses = statuses,
            rowRequesters = rowRequesters,
            onFocusSection = { onSelectSection(it); onFocusInContentChange(false) },
            onEnterSection = { onSelectSection(it); onFocusInContentChange(true) },
            modifier =
                Modifier
                    .requiredWidth(TvSettingsIndexWidth)
                    .fillMaxHeight()
                    .focusProperties { right = entryRequester },
        )
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(tones.ruleSoft))
        TvPage {
            // Fresh per section, not shared: without this, leaving one
            // section scrolled deep and moving the index to another opens
            // it with that same offset, its own title scrolled away.
            val scroll = remember(section) { ScrollState(0) }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scroll)
                        .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)
                        // The Column itself carries no focus target, so a plain
                        // `left = …` on it would apply to every control inside
                        // it rather than only the ones at its left edge — Left
                        // between two swatches would leave the section instead
                        // of moving to the previous one. onExit only fires once
                        // a search inside the group already failed to find
                        // another candidate: Up/Down there means the edge of
                        // the section, which stays put rather than falling
                        // through to the index above or below it; Left there
                        // means the leftmost control, which is when the index
                        // row is the right place to send the remote.
                        .focusProperties {
                            onExit = {
                                when (requestedFocusDirection) {
                                    FocusDirection.Up, FocusDirection.Down -> cancelFocusChange()
                                    FocusDirection.Left -> rowRequesters.getValue(section).requestFocus()
                                    else -> Unit
                                }
                            }
                        }
                        .onFocusChanged { state -> if (state.hasFocus) onFocusInContentChange(true) }
                        .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                PageHead(
                    title = section.title,
                    eyebrow = section.pageEyebrow.uppercase(),
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    eyebrowColor = tones.quiet,
                    maxTitleSize = TvTypeScale.pageTitleMax,
                    eyebrowStyle = Eyebrow.copy(fontSize = TvTypeScale.eyebrow),
                )
                content(section, focusInContent, entryRequester)
            }
        }
    }
}
