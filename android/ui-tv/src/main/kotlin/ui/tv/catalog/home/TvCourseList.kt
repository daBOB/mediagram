package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Entry
import catalog.initialsOf
import catalog.spelledCountOf
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * Latest courses: an index, not plates — a course carries no artwork, so a
 * plate would be a poster-shaped blank (the phone's own reason,
 * `home-view.js:108-113`). The television twin of the phone's `CourseList`,
 * each row a [Card] (so a focus ring reads on it) rather than [TvTextRow]:
 * a course row here carries an initials tile beside its own two lines,
 * which a plain text row has no second column for.
 */
@Composable
internal fun TvCourseList(
    courses: List<Entry.Collection>,
    onOpen: (String) -> Unit,
    // Where the requester ends up (which row, at [focusAt]'s own index) —
    // whether and when it is actually asked to take focus is `TvHome`'s
    // own call, made once after its outer list has confirmed this whole
    // list is really composed, not this list's to decide on its own mount.
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    lastStop: Modifier = Modifier,
) {
    if (courses.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        courses.forEachIndexed { index, course ->
            key(course.key) {
                // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
                val ownRequester = remember { FocusRequester() }
                Card(
                    onClick = { onOpen(course.key) },
                    modifier =
                        Modifier
                            .keepsInViewWhenMoved(index)
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .focusRequester(if (index == focusAt) focus ?: ownRequester else ownRequester)
                            .let { if (index == courses.lastIndex) it.then(lastStop) else it },
                    shape = TvFocus.cardShape(),
                    scale = TvFocus.cardScale(),
                    border = TvFocus.cardBorder(),
                    glow = TvFocus.cardGlow(),
                    colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Text(
                                text = initialsOf(course.name),
                                style = TvTypeScale.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        Column {
                            Text(text = course.name, style = TvTypeScale.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${spelledCountOf(course.count, "lesson")} · ${spelledCountOf(course.chapters, "chapter")}",
                                style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
