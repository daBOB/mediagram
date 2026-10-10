package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.TvType
import designsystem.TvType.Eyebrow
import designsystem.tvDp
import designsystem.tvSp
import model.MediaSet

/**
 * The contents page, numbered, in a ruled column — the television twin of
 * the phone's `ThisMonth`: one rule down the column's own left edge, and
 * one under each row.
 */
@Composable
internal fun TvThisMonth(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Max)) {
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.border))
        // `.this-month` (`home.css:307-321`): 28 in from its rule, rows 10 tall above and below, gap 8.
        Column(modifier = Modifier.padding(start = 28.tvDp)) {
            Text(
                text = "This month".uppercase(),
                style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.tvDp),
            )
            for ((index, set) in films.withIndex()) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) })
                                .padding(vertical = 10.tvDp),
                        horizontalArrangement = Arrangement.spacedBy(8.tvDp),
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = TvType.titleMedium.copy(fontSize = 20.8.tvSp, lineHeight = 1.em),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(35.2.tvDp),
                        )
                        Column {
                            Text(text = set.title, style = TvType.titleSmall.copy(fontSize = 15.tvSp, lineHeight = 1.3.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(set.year?.takeIf { it > 0 }?.toString(), set.genres.firstOrNull()).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = TvType.labelSmall.copy(fontSize = 12.tvSp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.border))
                }
            }
        }
    }
}
