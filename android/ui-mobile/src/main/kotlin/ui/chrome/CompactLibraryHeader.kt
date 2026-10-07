package ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagram.android.core.designsystem.R
import designsystem.LocalCatalogueTones
import ui.common.MenuActions
import ui.common.RailItem

/** The wordmark's own narrow-width sizes — the web's `.brand` at ≤900px, then ≤480px (`shell.css:184, 199`). */
private val CompactWordmarkBreakpoint = 480.dp

/**
 * The web's ≤900px header, three rows: wordmark and icon-only rail items,
 * the department pills, then a search-field look-alike with the avatar and
 * the trimmed overflow — `shell.css:190-215`. [ui.chrome.LibraryHome] hides
 * and reveals the whole thing on scroll; this only draws it.
 */
@Composable
internal fun CompactLibraryHeader(
    pills: List<DepartmentPill>,
    selectedPill: Int,
    onSelectPill: (Int) -> Unit,
    onHome: () -> Unit,
    activeRailItem: RailItem?,
    onRailSelect: (RailItem) -> Unit,
    onSearch: () -> Unit,
    profile: ProfileBarState,
    menu: MenuActions,
    onAskStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rail = LocalRailData.current
    val ink = MaterialTheme.colorScheme.onBackground
    val wordmarkSize = if (LocalConfiguration.current.screenWidthDp.dp <= CompactWordmarkBreakpoint) 21.sp else 23.sp

    val edgeFade = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .windowInsetsPadding(WindowInsets.statusBars)
                // A phone can be in EXPANDED landscape too (a folded/large
                // phone), with the 3-button nav bar on either side; this
                // header has no rail beside it to claim the start side the
                // way `DepartmentsBar` does, so both ends need it.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wordmark(
                fontSize = wordmarkSize,
                modifier =
                    Modifier
                        .clickable(onClick = onHome)
                        .semantics { contentDescription = "mediagram — home" },
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        // A narrow phone can run out of width before all seven
                        // rows fit; this hints there is more to reach for
                        // rather than cutting Settings/System off with
                        // nothing to say a sideways swipe finds them.
                        .fadeTrailingEdge(edgeFade),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (item in RailItem.entries) {
                    val selected = item == activeRailItem
                    val itemInk = if (selected) ink else MaterialTheme.colorScheme.onSurfaceVariant
                    CircleIconButton(icon = item.icon, description = item.label, tint = itemInk, selected = selected, onClick = { onRailSelect(item) }, dot = rail.dotFor(item))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pills.forEachIndexed { index, pill ->
                Pill(pill = pill, active = index == selectedPill, ink = ink, horizontalPadding = 12.dp, onClick = { onSelectPill(index) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchFieldLookAlike(onSearch = onSearch, modifier = Modifier.weight(1f))
            ChromeAvatar(profile = profile)
            AndroidOnlyMenu(menu = menu, onAskStartOver = onAskStartOver, tint = ink)
        }
    }
}

/** A field that opens the real search screen on tap rather than filtering in place — the web's own `.search`, which is also a link until it is focused (`shell.css:118-131`). */
@Composable
private fun SearchFieldLookAlike(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = LocalCatalogueTones.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(tones.ruleSoft)
                .clickable(onClick = onSearch)
                .semantics { contentDescription = "Search" }
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.core_designsystem_ic_search),
            contentDescription = null,
            tint = tones.quiet,
            modifier = Modifier.size(21.dp),
        )
        Text("Search titles and summaries", style = MaterialTheme.typography.bodyMedium, color = tones.quiet)
    }
}

/** A hint that there is more to reach for sideways — a 24dp fade to the header's own background at the trailing edge, drawn regardless of whether the row actually overflows: cheap, and never wrong to show. */
private fun Modifier.fadeTrailingEdge(background: Color): Modifier =
    drawWithContent {
        drawContent()
        val fadeWidth = 24.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, background), startX = size.width - fadeWidth, endX = size.width),
        )
    }
