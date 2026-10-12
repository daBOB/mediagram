package ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.SearchFilter
import designsystem.Motion
import designsystem.Spacing

/**
 * One pill per kind of hit, with its count, the chosen one filled: the
 * web's `.search-filter` row (`catalog.css:150-153`). Choosing one fades its
 * ink and fill over `--hover` (`:151`), keyed by kind so a list that gains or
 * loses a kind while typing never fades one pill into another's colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchFilterChips(filters: List<Pair<SearchFilter, Int>>, chosen: SearchFilter, onChoose: (SearchFilter) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        for ((kind, count) in filters) {
            key(kind) { FilterChip("${filterLabel(kind)} $count", selected = kind == chosen, onClick = { onChoose(kind) }) }
        }
    }
}

/** Its own scope, so only the chip being chosen or unchosen recomposes while its colours fade. */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = Motion.hover(),
        label = "filter ink",
    )
    val fill by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceVariant,
        animationSpec = Motion.hover(),
        label = "filter fill",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        modifier = Modifier
            // Clipped, so the fill and the press tint both keep the pill's shape.
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { drawRect(fill) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
    )
}

private fun filterLabel(filter: SearchFilter): String = when (filter) {
    SearchFilter.ALL -> "All"
    SearchFilter.MOVIES -> "Movies"
    SearchFilter.SERIES -> "Series"
    SearchFilter.ANIME -> "Anime"
    SearchFilter.DOCUMENTARIES -> "Documentaries"
    SearchFilter.TUTORIALS -> "Tutorials"
    SearchFilter.PEOPLE -> "People"
    SearchFilter.COLLECTIONS -> "Collections"
}
