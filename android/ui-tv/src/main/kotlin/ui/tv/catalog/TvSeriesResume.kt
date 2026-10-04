package ui.tv.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.SeriesResumePick
import catalog.resumeWordsOf
import designsystem.Spacing
import ui.tv.TvTextRow

/**
 * "Resume"/"Continue"/"Play" — [catalog.seriesResume]'s own pick, as a row
 * under a course's page head.
 */
@Composable
internal fun SeriesResumeRow(
    pick: SeriesResumePick,
    onResume: (setId: String) -> Unit,
) {
    TvTextRow(
        text = resumeLabel(pick),
        onClick = { onResume(pick.set.setId) },
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/** `▶ Resume S1 E3`, `▶ Continue lesson 4` — the phone's and the web's resume words ([resumeWordsOf]) after the play mark. */
internal fun resumeLabel(pick: SeriesResumePick): String = "▶ ${resumeWordsOf(pick)}"
