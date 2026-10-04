package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ui.catalog.home.fluid

/**
 * A reference page's own head — the web's `heading()` (`shelf-view.js`) as
 * `.shelf-head` sets it (`catalog.css`): the title in the display face, how
 * much is there in small tracked capitals flush right (or on its own line
 * once the title takes the width), and a rule under both. Latest, Genres, a
 * genre and a person open with this.
 *
 * Not [designsystem.PageHead]: that is a department's opener, a name in
 * huge capitals; the web gives these plainer pages this smaller head.
 * [leading] goes above the title — a person's portrait, which the web
 * prepends to the same header.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfHead(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)) {
        leading?.let {
            it()
            Spacer(Modifier.height(16.dp))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fluid(35.2f, 0.044f, 57.6f, width).sp,
                        lineHeight = 1.em,
                        letterSpacing = (-0.03).em,
                    ),
                // The web's 24px column gap, kept as the title's own end
                // margin so a sub that wraps onto its own line still starts
                // flush left, as `space-between` leaves a lone item.
                modifier = Modifier.padding(end = 24.dp).semantics { heading() },
            )
            sub?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.24.em),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.Bottom),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A part of such a page — "Movies", "Series", "Films" — the web's `.shelf-sub` (`catalog.css`), as a full-width item of the page's grid. */
internal fun LazyGridScope.shelfSub(label: String) {
    item(key = "heading-$label", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 1.2.em),
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
        )
    }
}
