package ui.tv.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import catalog.SeriesResumePick
import catalog.resumeWordsOf
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot
import ui.common.catalog.TogetherJoin
import ui.tv.TvTextRow

/**
 * A show's pills — `series-page.js`'s own: the resume pick ([resumeLabel])
 * when there is one, My List and ⋯ for the show's first episode, which is
 * how the web keeps a show on the list and pins it. While a watch-together
 * session plays one of its episodes, [join] stands in for the resume pick.
 * [play] and [listPill] are always attached to their own pill, so a page
 * can land the remote on whichever leads.
 */
@Composable
internal fun TvSeriesPills(
    first: MediaSet?,
    resume: SeriesResumePick?,
    onResume: (setId: String) -> Unit,
    watch: WatchSnapshot,
    onToggleWatchlist: () -> Unit,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
    play: FocusRequester,
    listPill: FocusRequester,
    join: TogetherJoin? = null,
) {
    TvPillRow {
        // One pill either way, so Join standing in for Resume keeps the remote on it.
        (if (join != null) "▶ Join" else resume?.let(::resumeLabel))?.let { label ->
            TvSpreadPill(
                text = label,
                onClick = join?.join ?: { if (resume != null) onResume(resume.set.setId) },
                solid = true,
                modifier = Modifier.focusRequester(play),
            )
        }
        if (first != null) {
            TvListPill(watchlisted = first.setId in watch.watchlist, onToggle = onToggleWatchlist, modifier = Modifier.focusRequester(listPill))
            TvMorePill(editorsChoiceChoice(onToggleEditorsChoice, pinned = editorsChoice == first.setId))
        }
    }
}

/**
 * "Resume"/"Continue"/"Play" — [catalog.seriesResume]'s own pick, as a row
 * under a course's page head; Join instead while a watch-together session
 * plays one of the course's lessons ([join]). One row either way, so the
 * remote on it stays there when a session starts or ends.
 */
@Composable
internal fun SeriesResumeRow(
    pick: SeriesResumePick?,
    onResume: (setId: String) -> Unit,
    join: TogetherJoin? = null,
) {
    val text = if (join != null) "▶ Join" else pick?.let(::resumeLabel) ?: return
    TvTextRow(
        text = text,
        onClick = join?.join ?: { if (pick != null) onResume(pick.set.setId) },
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/** `▶ Resume S1 E3`, `▶ Continue lesson 4` — the phone's and the web's resume words ([resumeWordsOf]) after the play mark. */
internal fun resumeLabel(pick: SeriesResumePick): String = "▶ ${resumeWordsOf(pick)}"
