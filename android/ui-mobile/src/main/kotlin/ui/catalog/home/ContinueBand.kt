package ui.catalog.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.SetCard
import model.MediaSet

/**
 * Continue Watching beside a pull-quote, one band sharing a baseline — a
 * Compose port of the `.home-band` around `resumeCards`/`pullQuote`
 * (`home-view.js:74-88`). Either half may be absent; the other then spans
 * the whole band. The cards and the quote themselves are `ResumeCard.kt`'s
 * own.
 */
@Composable
internal fun ContinueBand(
    cards: List<SetCard>,
    quote: MediaSet?,
    width: Dp,
    onPlay: (String) -> Unit,
    onOpenTitle: (String) -> Unit,
    onSeeAllContinue: () -> Unit,
) {
    if (cards.isEmpty() && quote == null) return
    val gutter = gutterFor(width)
    val compact = width <= CompactBreakpoint
    val continueBlock: @Composable () -> Unit = {
        if (cards.isNotEmpty()) {
            Column {
                BandHeading(title = "Continue Watching", count = null, onSeeAll = onSeeAllContinue)
                ResumeRow(cards = cards, onPlay = onPlay, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
    val quoteBlock: @Composable () -> Unit = { quote?.let { Quote(it, width, onOpenTitle) } }

    if (compact) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            continueBlock()
            quoteBlock()
        }
        return
    }
    when {
        cards.isEmpty() -> Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { quoteBlock() }
        quote == null -> Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { continueBlock() }
        else ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(fluid(32f, 0.04f, 72f, width.value).dp),
            ) {
                Box(Modifier.weight(2.6f)) { continueBlock() }
                Box(Modifier.weight(1f).widthIn(min = 256.dp)) { quoteBlock() }
            }
    }
}

/** A row's own header: the name, and the way to the whole wall — `.row-head` (`home.css:194-210`), shared by every section on this page. */
@Composable
internal fun BandHeading(
    title: String,
    count: Int?,
    onSeeAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.8.sp, letterSpacing = (-0.01).em),
            )
            if (count != null) {
                Text(
                    text = " $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Text(
            text = "See all →",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(role = Role.Button, onClick = onSeeAll)
                    .padding(vertical = 12.dp),
        )
    }
}
