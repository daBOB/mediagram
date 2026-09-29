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
import androidx.compose.runtime.LaunchedEffect
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
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    lastStop: Modifier = Modifier,
    // Read fresh inside the effect below, never added to its own key: a
    // sentinel elsewhere (Search, the bar's ⋮) can make this read `false`
    // for exactly one composition and then flip back to `true` once it is
    // consumed, with `focusAt` itself unchanged throughout — keying on it
    // too would re-run the request and steal the remote right back.
    takesFocus: Boolean = true,
) {
    if (courses.isEmpty()) return
    // Not a lazy list, so nothing here has to be scrolled into place first
    // — arrival only ever has to call `requestFocus()` once this row is
    // composed at all.
    LaunchedEffect(focusAt) {
        if (focusAt == null || focus == null || !takesFocus) return@LaunchedEffect
        focus.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        courses.forEachIndexed { index, course ->
            Card(
                onClick = { onOpen(course.key) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .let { if (index == focusAt && focus != null) it.focusRequester(focus) else it }
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
