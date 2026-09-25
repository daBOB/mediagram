package ui.tv.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Text
import catalog.factsLine
import catalog.resumeLine
import data.ProgressPoint
import data.ResumePoint
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import model.Progress
import model.ageLabel
import player.technicalLine
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * What a title is, before playing it — the television twin of the phone's
 * `TitleDetailScreen` and the web's film page: the art beside the facts,
 * what the file is, what a provider said about it, and Play.
 *
 * Play takes the remote the moment the page appears. It is the one thing
 * here to press and the reason a viewer came, so a single centre press
 * from the plate that opened this starts the title. It says Resume when
 * [progress] is a place the player will actually carry on from —
 * [ResumePoint.resumeAt], the rule the player and the web's film page both
 * start by, so a glance at the opening or a position in the credits says
 * Play — over the line that says where, in [resumeLine]'s words, the same
 * the Continue wall captions its plates with. The phone's title page always
 * says Play; this follows the web's page, which says Resume.
 *
 * [info] being null is ordinary rather than a failure, for the phone's
 * reason: a course has no provider entry, and a library assembled without
 * a TMDB key has none at all, so each block it would fill is left out.
 */
@Composable
internal fun TvTitlePage(
    set: MediaSet,
    info: TitleInfo?,
    progress: Progress?,
    onPlay: () -> Unit,
) {
    val play = remember { FocusRequester() }
    val resume =
        remember(progress) {
            val resumes = progress?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) } != null
            if (resumes) resumeLine(progress) else ""
        }

    TvPage {
        TvTitleHeader(
            posterPath = set.posterPath,
            title = set.title,
            facts = factsLine(set.year, set.durationSecs, set.ageLabel()),
            info = info,
            genres = set.genres,
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            readableOverview = true,
        ) {
            // As stored, not shouted, for the phone's reason: the web prints
            // the container and codecs in the case the index recorded them.
            technicalLine(set).takeIf(String::isNotEmpty)?.let { TvQuietLine(it) }
            if (resume.isNotEmpty()) Text(text = resume, style = TvTypeScale.body)
            TvTextRow(
                text = if (resume.isEmpty()) "▶ Play" else "▶ Resume",
                onClick = onPlay,
                modifier = Modifier.padding(top = Spacing.small),
                focusRequester = play,
            )
        }
    }
    LaunchedEffect(set.setId) { play.requestFocus() }
}
