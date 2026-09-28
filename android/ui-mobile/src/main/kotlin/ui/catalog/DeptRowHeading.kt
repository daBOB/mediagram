package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing

/** A row's name, with an optional "See all" and an optional control beside it — the Compose port of `deptRow`'s own header. */
@Composable
internal fun DeptRowHeading(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See all",
    extra: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        extra?.invoke()
        if (onSeeAll != null) {
            Text(
                text = seeAllLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(start = Spacing.medium),
            )
        }
    }
}
