package ui.tv.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import catalog.ChromeCounts
import designsystem.LocalCatalogueTones
import designsystem.TvType
import designsystem.webDp
import designsystem.webSp
import stats.NEW_ACHIEVEMENT
import ui.common.RailItem
import ui.tv.TvIndexRow

/*
 * The web's rail at the TV's 1280px reading (`shell.css:17-70`): 14rem, 18 in from either side,
 * each row 12 around a 21 icon. The web has no collapsed rail at that width (under 900px it is a
 * header), so collapsed is the open rail's icon column, 18 + 12 + 21 + 12 + 18, and opening it
 * moves no icon. Held to the 48dp overscan margin, the icons stood 56dp in: more than the web's.
 */
private val RailSide = 18.webDp
private val RailRowPadding = PaddingValues(horizontal = 12.webDp, vertical = 10.webDp)
private val RailIcon = 21.webDp
internal val RailCollapsedWidth = RailSide + 12.webDp + RailIcon + 12.webDp + RailSide
internal val RailOpenWidth = 224.webDp

/** The rows drawn before System's own gap — [RailItem.SYSTEM] is added apart, after this list. */
private val RailRowsBeforeSystem = listOf(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING, RailItem.LATEST, RailItem.GENRES, RailItem.STATS, RailItem.SETTINGS)

/** The web's `.brand`: Fraunces 600 at 1.7rem, tracked in (`shell.css:29-37`) — the tablet's own `Wordmark`. */
private val TvWordmark = TvType.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 27.2.webSp, letterSpacing = (-0.02).em)

private val WordmarkBelow = 36.webDp // `.brand`'s `margin-bottom`; its `padding: 0 10px` is the start inset.

/**
 * Whether the chosen profile has earned something this device has not shown
 * it yet — the dot on the Stats row. Provided by the library frame that
 * draws the catalogue, and read here rather than threaded through the
 * catalogue screen and its chrome, which have nothing else to do with it.
 */
internal val LocalNewAchievement = compositionLocalOf { false }

/**
 * The library rail: icons alone at [RailCollapsedWidth] while the remote is
 * elsewhere, opening over the content at [RailOpenWidth] — wordmark, rows,
 * tally — the moment the remote reaches it, closing again the moment it
 * leaves. [expanded] is this composable's own `hasFocus`, not a value
 * threaded in: the rail's own focus state is exactly what should drive it,
 * and deriving it here rather than in [TvLibraryChrome] keeps whichever row
 * gaining focus in step with the width animating with it in the same frame.
 *
 * [active] rings the current kept row (My List/Continue) when a
 * kept wall is showing and no departments pill is the current one; every
 * other row is never "selected", only ever focused or not.
 *
 * Up/Down stop at the rail's own ends rather than escaping to whatever
 * geometrically sits above or below across the rail's edge; Right from any
 * row runs [onRight], which sends the remote back to where it came from:
 * the bar's pill, or the page's own last stop.
 */
@Composable
internal fun TvLibraryRail(
    active: RailItem?,
    counts: ChromeCounts,
    tally: List<String>,
    rowRequesters: Map<RailItem, FocusRequester>,
    onSelect: (RailItem) -> Unit,
    onRight: () -> Unit,
    onHasFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailOpenWidth else RailCollapsedWidth, label = "tvRailWidth")
    val tones = LocalCatalogueTones.current

    Column(
        modifier =
            modifier
                .width(width)
                .fillMaxHeight()
                .background(tones.sidebar)
                .onFocusChanged { state ->
                    expanded = state.hasFocus
                    onHasFocusChanged(state.hasFocus)
                }
                .focusProperties {
                    onExit = {
                        when (requestedFocusDirection) {
                            FocusDirection.Up, FocusDirection.Down -> cancelFocusChange()
                            FocusDirection.Right -> onRight()
                            else -> Unit
                        }
                    }
                }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(start = RailSide, end = RailSide, top = 26.webDp, bottom = 28.webDp),
    ) {
        // Laid out either way, and only hidden while collapsed, so the rows land at the same height.
        Text(
            text = "mediagram",
            style = TvWordmark,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 10.webDp, bottom = WordmarkBelow).then(if (expanded) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
        )

        // `.rail-nav`'s 2px gap, and System's `.apart` 12 on top of it.
        Column(verticalArrangement = Arrangement.spacedBy(2.webDp)) {
            for (item in RailRowsBeforeSystem) {
                RailRow(item = item, active = item == active, counts = counts, expanded = expanded, focusRequester = rowRequesters.getValue(item), onSelect = onSelect)
            }
            val system = RailItem.SYSTEM
            RailRow(system, system == active, counts, expanded, rowRequesters.getValue(system), onSelect, Modifier.padding(top = 12.webDp))
        }

        if (expanded && tally.isNotEmpty()) {
            val tallyStyle =
                TvType.labelSmall.copy(fontSize = 10.webSp, fontWeight = FontWeight.Medium, letterSpacing = 0.34.em, lineHeight = 2.em)
            // `.rail-masthead`: margin 28 10 0 carries the rule, padding 24 6 0 the lines.
            Box(modifier = Modifier.padding(start = 10.webDp, end = 10.webDp, top = 28.webDp).fillMaxWidth().height(1.dp).background(tones.ruleSoft))
            Column(modifier = Modifier.padding(start = 16.webDp, end = 16.webDp, top = 24.webDp)) {
                for (line in tally) {
                    Text(text = line.uppercase(), style = tallyStyle, color = tones.quiet)
                }
            }
        }
    }
}

@Composable
private fun RailRow(
    item: RailItem,
    active: Boolean,
    counts: ChromeCounts,
    expanded: Boolean,
    focusRequester: FocusRequester,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvIndexRow(
        icon = painterResource(item.icon),
        label = item.label,
        selected = active,
        focusRequester = focusRequester,
        onSelect = { onSelect(item) },
        modifier = modifier,
        expanded = expanded,
        minHeight = 44.webDp,
        contentPadding = RailRowPadding,
        iconSize = RailIcon,
        iconGap = 12.webDp,
        alignment = Alignment.CenterVertically,
        // Counts only once open: the web's icons-only form shows none.
        trailing = if (expanded) countFor(item, counts)?.toString() else null,
        contentDescription = item.label,
        dot = if (item == RailItem.STATS && LocalNewAchievement.current) NEW_ACHIEVEMENT else null,
    )
}

/** [ChromeCounts] carries a number for the two kept rows only — the rest read nothing after their name, the same as the web's own rail. */
private fun countFor(item: RailItem, counts: ChromeCounts): Int? =
    when (item) {
        RailItem.MY_LIST -> counts.myList
        RailItem.CONTINUE_WATCHING -> counts.continueWatching
        else -> null
    }
