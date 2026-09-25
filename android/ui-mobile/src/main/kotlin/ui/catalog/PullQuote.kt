package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import model.MediaSet

/**
 * A film's tagline as a pull-quote, credited to the film — a Compose port
 * of `home-break.js`'s `pullQuote`. The typographic break between the
 * resume strip and what follows, the same job it does on the web.
 */
@Composable
internal fun PullQuote(
    set: MediaSet,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagline = set.tagline ?: return
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpenTitle(set.setId) }
                .padding(vertical = Spacing.large, horizontal = Spacing.medium),
    ) {
        Text(
            text = "“$tagline”",
            style = MaterialTheme.typography.headlineSmall,
        )
        val credit = listOfNotNull(set.title, set.year?.takeIf { it > 0 }?.toString()).joinToString(", ")
        Text(
            text = "— $credit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
    }
}
