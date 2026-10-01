package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import catalog.SetCard
import designsystem.Spacing
import ui.catalog.rememberRowState
import ui.tv.catalog.home.TvBandHeading
import ui.tv.catalog.home.TvResumeCard

/** [DeptCacheAhead]'s own value (`TvDepartmentRows.kt`), at a resume card's own, wider scale. */
private val ResumeCacheAhead = 320.dp

/**
 * A department page's own Continue row — Series/Tutorials' "Continue your
 * series"/"…courses", Anime's and Documentaries' "Continue watching" — the
 * requirement's own resume cards (landscape, progress, OK plays) rather
 * than [DeptEntryRow]'s plain poster plates, which open a title page: a card
 * here stands for a title already underway, and the web's own `home-resume.js`
 * plays it back exactly where it stopped, never a synopsis first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeptResumeRow(
    title: String,
    cards: List<SetCard>,
    onPlay: (setId: String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    takesFocus: Boolean = true,
    onSectionFocused: (() -> Unit)? = null,
) {
    if (cards.isEmpty()) return
    Box(Modifier.padding(top = Spacing.large)) { TvBandHeading(title = title, count = null) }
    val inRow = remember { mutableStateOf(false) }
    val state = rememberRowState(cards.map { it.set.setId }, rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = ResumeCacheAhead, behind = ResumeCacheAhead) }), inUse = { inRow.value })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(
        state = state,
        modifier =
            Modifier
                .padding(top = Spacing.small)
                .onFocusChanged { s ->
                    inRow.value = s.hasFocus
                    if (s.hasFocus) onSectionFocused?.invoke()
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        itemsIndexed(cards, key = { _, card -> card.set.setId }) { index, card ->
            TvResumeCard(
                card = card,
                onOpen = { onPlay(card.set.setId) },
                focusRequester = if (index == focusAt && focus != null) focus else null,
            )
        }
    }
}
