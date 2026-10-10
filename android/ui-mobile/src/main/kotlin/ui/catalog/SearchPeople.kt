package ui.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.BrowseViewModel
import catalog.VisiblePerson
import catalog.spelledCountOf
import designsystem.Spacing
import designsystem.hoverMotion
import ui.chrome.linkClickable
import ui.common.catalog.rememberPortrait

/**
 * People among the hits, as round portraits: the web's search draws the
 * same `personCard` the Cast tab does (`search-view.js:127-130`), so a face
 * lifts for a hovering pointer here as it does there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleSection(people: List<VisiblePerson>, onOpenPerson: (Long) -> Unit) {
    val browseViewModel: BrowseViewModel = hiltViewModel()
    Column(modifier = Modifier.padding(top = Spacing.medium)) {
        Text("People", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (person in people) {
                // Fetched lazily, at most once per session per person — the
                // same [rememberPortrait] a person's own page asks with.
                val portrait = rememberPortrait(person.personId, person.portraitPath, browseViewModel.portraits, browseViewModel::fetchPortrait)
                val source = remember { MutableInteractionSource() }
                Column(
                    modifier =
                        Modifier
                            // A person is a link (`cast.js:49`), and links don't shrink.
                            .linkClickable(source) { onOpenPerson(person.personId) }
                            .padding(Spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // `.person:hover .person-face` rises 3px over 520ms (`title-page.css:178,180`).
                    Box(Modifier.hoverMotion(source, durationMs = 520, lift = (-3).dp)) {
                        PersonFace(name = person.name, portraitPath = portrait, size = 64.dp)
                    }
                    Text(person.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.extraSmall))
                    Text(
                        spelledCountOf(person.titles, "title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
