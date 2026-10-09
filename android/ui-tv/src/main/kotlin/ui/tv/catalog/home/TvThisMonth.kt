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
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.CatalogueType
import designsystem.Eyebrow
import designsystem.Spacing
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
        Column(modifier = Modifier.padding(start = Spacing.medium)) {
            Text(
                text = "This month".uppercase(),
                style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
            for ((index, set) in films.withIndex()) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) })
                                .padding(vertical = Spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = CatalogueType.titleMedium.copy(fontSize = 20.8.sp, lineHeight = 1.em),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(35.dp),
                        )
                        Column {
                            Text(text = set.title, style = CatalogueType.titleSmall.copy(fontSize = 15.sp, lineHeight = 1.3.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(set.year?.takeIf { it > 0 }?.toString(), set.genres.firstOrNull()).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = CatalogueType.labelSmall.copy(fontSize = 12.sp),
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
