package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import catalog.factsLine
import catalog.ratingLabel
import catalog.resumeLine
import data.ProgressPoint
import data.ResumePoint
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import model.Progress
import model.TitleCredits
import model.ageLabel
import player.technicalLine
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * What a title is, before playing it — the television twin of the phone's
 * `TitleDetailScreen` and the web's film page: the art beside the facts,
 * then Overview/Cast/Similar/Details, the same split the web's film page
 * tabs into.
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
 * Its genres open their own pages through [onOpenGenre]; coming back from
 * one, [restoreKey] names it and that link takes the remote instead of
 * Play, so Back lands where the viewer was.
 *
 * [info] being null is ordinary rather than a failure, for the phone's
 * reason: a course has no provider entry, and a library assembled without
 * a TMDB key has none at all, so each block it would fill is left out.
 *
 * Cast and Similar are only offered as tabs once there is something to put
 * on them — [credits] with an empty `cast` and an empty [similar] both drop
 * their own tab, the same "nothing to show, nothing to tap into" rule every
 * other empty state on this surface follows. [editorsChoice]/[onToggleEditorsChoice]
 * mirror the phone's own pin: `null` on a kids profile, since a household
 * mark is not a kids profile's to make.
 */
@Composable
internal fun TvTitlePage(
    set: MediaSet,
    info: TitleInfo?,
    progress: Progress?,
    onPlay: () -> Unit,
    onOpenGenre: (String) -> Unit = {},
    restoreKey: String? = null,
    credits: TitleCredits = TitleCredits.Empty,
    onOpenPerson: (personId: Long) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
    similar: List<MediaSet> = emptyList(),
    onOpenTitle: (setId: String) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
) {
    val play = remember { FocusRequester() }
    val resume =
        remember(progress) {
            val resumes = progress?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) } != null
            if (resumes) resumeLine(progress) else ""
        }
    val tabs =
        remember(credits, similar) {
            buildList {
                add("Overview")
                if (credits.cast.isNotEmpty()) add("Cast")
                if (similar.isNotEmpty()) add("Similar")
                add("Details")
            }
        }
    var selected by rememberSaveable(set.setId) { mutableIntStateOf(0) }
    if (selected >= tabs.size) selected = 0

    TvPage {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = Overscan.vertical)) {
            TvSectionTabs(
                titles = tabs,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = Overscan.horizontal),
            )
            when (tabs[selected]) {
                "Cast" ->
                    TvPageBody {
                        TvCastRow(credits, onOpenPerson, shouldRequestPortrait, fetchPortrait)
                    }

                "Similar" ->
                    TvPageBody {
                        TvSimilarFilms(similar, onOpenTitle)
                    }

                "Details" ->
                    TvPageBody {
                        TvTitleDetails(set, info, editorsChoice, onToggleEditorsChoice)
                    }

                else ->
                    TvTitleHeader(
                        posterPath = set.posterPath,
                        title = set.title,
                        facts = factsLine(set.year, set.durationSecs, set.ageLabel()),
                        info = info,
                        genres = set.genres,
                        onOpenGenre = onOpenGenre,
                        genreFocus = restoreKey,
                        // The page scrolls inside the overscan-safe band rather
                        // than across the whole screen — see the class doc for
                        // why the inset is applied on the outer Column instead.
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .testTag(TvTitlePageBodyTag)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = Overscan.horizontal),
                        readableOverview = true,
                    ) {
                        // As stored, not shouted: the web prints the container
                        // and codecs in the case the index recorded them.
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
        }
    }
    LaunchedEffect(set.setId, selected) {
        if (selected == 0 && restoreKey !in set.genres) play.requestFocus()
    }
}

/** A tab's own body, scrolled the same way the Overview tab's header already is. */
@Composable
private fun TvPageBody(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(TvTitlePageBodyTag)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal, vertical = Spacing.medium),
    ) {
        content()
    }
}

/**
 * The scrollable body's own tag — the tab row above it (`TvSectionTabs`)
 * carries a horizontal scroll capability of its own on a television-wide
 * tab strip, so a test asking for "the" scrollable node by
 * `hasScrollAction()` alone finds two; this is the one that is actually the
 * page's own body, whichever tab is showing.
 */
internal const val TvTitlePageBodyTag = "tv-title-page-body"

/**
 * Details: the file as it sits on disk, the provider's rating and status,
 * and the editor's-choice toggle — the phone's own "Make editor's
 * choice"/"Remove as editor's choice" button, ported to a television for
 * the first time by this phase. `null` [onToggleEditorsChoice] hides the
 * row entirely, same as the phone's kids-profile gate.
 */
@Composable
private fun TvTitleDetails(
    set: MediaSet,
    info: TitleInfo?,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
) {
    Column {
        technicalLine(set).takeIf(String::isNotEmpty)?.let { Text(text = it, style = TvTypeScale.body) }
        ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.network?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.status?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        if (onToggleEditorsChoice != null) {
            val pinned = editorsChoice == set.setId
            TvTextRow(
                text = if (pinned) "Remove as editor's choice" else "Make editor's choice",
                onClick = onToggleEditorsChoice,
                modifier = Modifier.padding(top = Spacing.medium),
            )
        }
    }
}
