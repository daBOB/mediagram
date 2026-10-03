package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvTypeScale
import stats.ACHIEVEMENTS_HEADING
import stats.AchievementsUi
import stats.NEXT_HEADING
import ui.tv.TvFocus

/**
 * The Stats page's Achievements on a television: the phone's section at a
 * television's sizes, with the same strings. Every line is a stop for the
 * remote, as every history line is — the page has nothing to press, but
 * stepping is what scrolls it. Nothing at all while there is neither an
 * earned achievement nor one to come.
 */
internal fun LazyListScope.tvAchievementItems(achievements: AchievementsUi) {
    if (achievements.earned.isEmpty() && achievements.next.isEmpty()) return
    item(key = "achievements") {
        Text(text = ACHIEVEMENTS_HEADING, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
    }
    items(items = achievements.earned, key = { "achievement:${it.id}" }) { line ->
        TvStatsStop { focused -> TvAchievementLine(line.label, line.on, focused) }
    }
    if (achievements.next.isEmpty()) return
    item(key = "achievements-next") {
        Text(text = NEXT_HEADING, style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, color = LocalCatalogueTones.current.quiet))
    }
    items(items = achievements.next, key = { "next:${it.id}" }) { line ->
        TvStatsStop { focused ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                TvAchievementLine(line.label, line.progress, focused)
                TvProgressRule(fraction = line.fraction)
            }
        }
    }
}

@Composable
private fun TvAchievementLine(
    label: String,
    detail: String,
    focused: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = TvFocus.textStyle(TvTypeScale.body, focused))
        Text(text = detail, style = TvTypeScale.body.copy(color = LocalCatalogueTones.current.quiet))
    }
}
