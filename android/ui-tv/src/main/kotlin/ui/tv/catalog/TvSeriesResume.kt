package ui.tv.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.ResumeVerb
import catalog.SeriesResumePick
import catalog.episodeShort
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

/** `▶ Resume S1 E3` — `series-page.js`'s own pill words, [ResumeVerb] naming which of the three it is. */
internal fun resumeLabel(pick: SeriesResumePick): String {
    val verb =
        when (pick.verb) {
            ResumeVerb.RESUME -> "Resume"
            ResumeVerb.CONTINUE -> "Continue"
            ResumeVerb.PLAY -> "Play"
        }
    return "▶ $verb ${episodeShort(pick.set)}"
}
