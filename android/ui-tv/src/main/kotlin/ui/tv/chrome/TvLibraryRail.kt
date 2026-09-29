package ui.tv.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import catalog.ChromeCounts
import designsystem.LocalCatalogueTones
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.RailItem
import ui.tv.TvIndexRow

/** Collapsed: the web's own icons-only form of the rail, `shell.css:184-216`; open: the tablet's full rail. */
private val RailCollapsedWidth = 96.dp
internal val RailOpenWidth = 288.dp

/** The rows drawn before System's own gap — [RailItem.SYSTEM] is added apart, after this list. */
private val RailRowsBeforeSystem = listOf(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING, RailItem.LATEST, RailItem.GENRES, RailItem.SETTINGS)

/** The wordmark's own size on a 288dp rail — narrower than [designsystem.TvTypeScale.title], the same lesson the tablet's own narrow rail already drew for the same reason: it wraps at that size in this little room. */
private val TvWordmarkSize = 24.sp

/**
 * The library rail: icons alone at [RailCollapsedWidth] while the remote is
 * elsewhere, opening over the content at [RailOpenWidth] — wordmark, rows,
 * tally — the moment the remote reaches it, closing again the moment it
 * leaves. [expanded] is this composable's own `hasFocus`, not a value
 * threaded in: the rail's own focus state is exactly what should drive it,
 * and deriving it here rather than in [TvLibraryChrome] keeps whichever row
 * gaining focus in step with the width animating with it in the same frame.
 *
 * [active] rings the current kept row (My List/Continue watching) when a
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
                .padding(start = Overscan.horizontal, end = Spacing.medium, top = Overscan.vertical, bottom = Overscan.vertical),
    ) {
        if (expanded) {
            Text(
                text = "mediagram",
                style = TvTypeScale.title.copy(fontSize = TvWordmarkSize, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = Spacing.large),
            )
        } else {
            Spacer(Modifier.height(WordmarkRowHeight))
        }

        for (item in RailRowsBeforeSystem) {
            RailRow(item = item, active = item == active, counts = counts, expanded = expanded, focusRequester = rowRequesters.getValue(item), onSelect = onSelect)
        }
        RailRow(
            item = RailItem.SYSTEM,
            active = RailItem.SYSTEM == active,
            counts = counts,
            expanded = expanded,
            focusRequester = rowRequesters.getValue(RailItem.SYSTEM),
            onSelect = onSelect,
            modifier = Modifier.padding(top = Spacing.medium),
        )

        if (expanded && tally.isNotEmpty()) {
            val tallyStyle =
                TvTypeScale.body.copy(fontWeight = FontWeight.Medium, fontSize = TvTypeScale.eyebrow, letterSpacing = 0.34.em, lineHeight = 2.em)
            Box(modifier = Modifier.padding(top = Spacing.large).fillMaxWidth().height(1.dp).background(tones.ruleSoft))
            Column(modifier = Modifier.padding(top = Spacing.large)) {
                for (line in tally) {
                    Text(text = line.uppercase(), style = tallyStyle, color = tones.quiet)
                }
            }
        }
    }
}

/** How tall the wordmark's own slot is while collapsed — kept as blank space so the six rows land at the same height either way. */
private val WordmarkRowHeight = 44.dp

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
        // Collapsed = the web's own icons-only form: no counts there at all,
        // shown only once the rail is open — `countFor` still runs every
        // frame regardless, cheap enough that gating it on `expanded` buys
        // nothing a reader would notice.
        trailing = if (expanded) countFor(item, counts)?.toString() else null,
        contentDescription = item.label,
    )
}

/** [ChromeCounts] carries a number for the two kept rows only — the rest read nothing after their name, the same as the web's own rail. */
private fun countFor(item: RailItem, counts: ChromeCounts): Int? =
    when (item) {
        RailItem.MY_LIST -> counts.myList
        RailItem.CONTINUE_WATCHING -> counts.continueWatching
        else -> null
    }
