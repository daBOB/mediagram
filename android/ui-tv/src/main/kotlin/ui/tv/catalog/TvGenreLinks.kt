package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import designsystem.Spacing
import ui.tv.TvTextRow

/**
 * Each genre as a stop the remote can press to open its page — the
 * television twin of the phone's `GenreLinks`, shared as that one is by a
 * film's own page and a show's header, the way `genreLinks` in the web's
 * `film-page.js` is. Nothing is drawn for a title with no genre.
 *
 * [focused] names the genre whose page was just left, which takes the
 * remote back the moment the links appear: Back from a genre page lands on
 * the link that opened it, not on whatever the page would otherwise put
 * first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvGenreLinks(
    names: List<String>,
    onOpen: (String) -> Unit,
    focused: String? = null,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return
    val back = remember { FocusRequester() }
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for (name in names) {
            TvTextRow(text = name, onClick = { onOpen(name) }, focusRequester = back.takeIf { name == focused })
        }
    }
    LaunchedEffect(focused) { if (focused in names) back.requestFocus() }
}
