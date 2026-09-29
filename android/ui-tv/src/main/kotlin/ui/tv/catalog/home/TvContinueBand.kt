package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.SetCard
import designsystem.CoverTitle
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import ui.tv.TvFocus
import ui.tv.TvTextRow

/**
 * Continue watching beside a pull-quote, one band sharing a baseline — the
 * television twin of the phone's `ContinueBand`. Either half may be absent;
 * the other then spans the whole band, the phone's own rule.
 *
 * [focusAt]/[focus] name the one card, if any, this band opens with —
 * `null` when the arrival target lies elsewhere and this band draws with no
 * requester attached at all.
 */
@Composable
internal fun TvContinueBand(
    cards: List<SetCard>,
    quote: MediaSet?,
    onPlay: (String) -> Unit,
    onOpenTitle: (String) -> Unit,
    onSeeAllContinue: () -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    // Read fresh inside the effect below, never added to its own key — see
    // [TvCourseList]'s own doc on the same parameter for why.
    takesFocus: Boolean = true,
) {
    if (cards.isEmpty() && quote == null) return
    val link = remember { SeeAllLink() }
    LaunchedEffect(focusAt) {
        if (focusAt == null || focus == null || !takesFocus) return@LaunchedEffect
        // A plain, always-fully-composed row (at most `HOME_ROW_LIMIT`
        // cards, not a plate wall's own hundreds): focusing straight away
        // is enough, since Compose's own scrollable-ancestor relocation
        // brings a newly focused card into view without this needing to
        // scroll it there itself first.
        focus.requestFocus()
    }

    val continueBlock: @Composable () -> Unit = {
        if (cards.isNotEmpty()) {
            Column {
                TvBandHeading(title = "Continue Watching", count = null) {
                    TvTextRow(text = "See all", onClick = onSeeAllContinue, modifier = link.seeAll, focusRequester = link.focus)
                }
                Row(
                    modifier = Modifier.padding(top = Spacing.medium).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    cards.forEachIndexed { index, card ->
                        TvResumeCard(
                            card = card,
                            onOpen = { onPlay(card.set.setId) },
                            focusRequester = if (index == focusAt) focus else null,
                            modifier = if (index == cards.lastIndex) link.lastStop else Modifier,
                        )
                    }
                }
            }
        }
    }
    val quoteBlock: @Composable () -> Unit = { quote?.let { TvQuote(it, onOpenTitle) } }

    when {
        cards.isEmpty() -> Box(Modifier.fillMaxWidth()) { quoteBlock() }
        quote == null -> Box(Modifier.fillMaxWidth()) { continueBlock() }
        else ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                Box(Modifier.weight(2.6f)) { continueBlock() }
                Box(Modifier.weight(1f).widthIn(min = 240.dp)) { quoteBlock() }
            }
    }
}

/** The typographic break — the phone's own `Quote`, the mark hung above the first line rather than in the margin beside it: TV has no `blockquote::before` absolute-positioning trick to reach for. */
@Composable
private fun TvQuote(
    set: MediaSet,
    onOpenTitle: (String) -> Unit,
) {
    val tagline = set.tagline ?: return
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) }),
    ) {
        Text(text = "“", style = CoverTitle.copy(fontSize = 40.sp, lineHeight = 1.em), modifier = Modifier.padding(bottom = Spacing.small))
        Text(
            text = tagline,
            style = TvFocus.textStyle(TvTypeScale.body.copy(fontStyle = FontStyle.Italic), focused),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.small).width(32.dp).height(1.dp).background(MaterialTheme.colorScheme.border))
        val credit = listOfNotNull(set.title, set.year?.takeIf { it > 0 }?.toString()).joinToString(", ").uppercase()
        Text(text = credit, style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
