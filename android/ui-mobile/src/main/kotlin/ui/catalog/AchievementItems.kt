package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import designsystem.Spacing
import stats.AchievementsUi

/**
 * The Stats page's Achievements, between the last thirty days and the
 * history (which has no end): what was earned and on which day, then the
 * closest few to come with how far along each is — the web's
 * `achievementsSection`. Nothing at all while there is neither.
 */
internal fun LazyListScope.achievementItems(achievements: AchievementsUi) {
    if (achievements.earned.isEmpty() && achievements.next.isEmpty()) return
    item(key = "achievements") { Text(text = "Achievements", style = MaterialTheme.typography.titleMedium) }
    items(items = achievements.earned, key = { "achievement:${it.id}" }) { line -> AchievementRow(line.label, line.on) }
    if (achievements.next.isEmpty()) return
    item(key = "achievements-next") {
        Text(text = "Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(items = achievements.next, key = { "next:${it.id}" }) { line ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            AchievementRow(line.label, line.progress)
            // The words above already say it; the bar is for the eye alone.
            LinearProgressIndicator(progress = { line.fraction }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        }
    }
}

@Composable
private fun AchievementRow(
    label: String,
    detail: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
