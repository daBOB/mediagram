package ui.tv.catalog.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.TvTypeScale

/**
 * A magazine band's own heading: its name, an optional count, "See all" at
 * the far end — the television twin of the phone's `BandHeading`
 * (`ContinueBand.kt`). Unlike [ui.tv.catalog.TvCountedHeading] (every plain
 * wall's own, mandatory total), [count] here is nullable: Home's own
 * Continue Watching band carries none, the same as the phone's.
 */
@Composable
internal fun TvBandHeading(
    title: String,
    count: Int?,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(
            text =
                buildAnnotatedString {
                    append(title)
                    if (count != null) withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · $count") }
                },
            style = TvTypeScale.title,
        )
        trailing()
    }
}

/**
 * The one way to "See all" and back, moved verbatim from the old
 * `TvHomeRow`'s own row: Right from the row's last stop, and Left or Down
 * from it to that stop again. It can take focus only while the remote is on
 * one or the other, so a search from anywhere else on the page never lands
 * on it.
 */
internal class SeeAllLink {
    val focus = FocusRequester()
    private val lastFocus = FocusRequester()
    private var fromLast by mutableStateOf(false)
    private var held by mutableStateOf(false)

    val seeAll: Modifier =
        Modifier
            .onFocusChanged { held = it.isFocused }
            .focusProperties {
                canFocus = fromLast || held
                left = lastFocus
                down = lastFocus
            }

    val lastStop: Modifier =
        Modifier
            .focusRequester(lastFocus)
            .onFocusChanged { fromLast = it.isFocused }
            .focusProperties { right = focus }
}
