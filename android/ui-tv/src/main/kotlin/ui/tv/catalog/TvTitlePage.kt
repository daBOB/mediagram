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
import catalog.franchisesIn
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
 * one, [restoreKey] names it and that link takes the remote instead of Play.
 * The same [restoreKey] also picks up whichever of Cast or Similar it was
 * opened from — a person's or a similar film's id — and drives both which
 * tab is initially showing and which of its plates takes focus, recomputed
 * fresh on every composition: this page is torn down and rebuilt on the way
 * back from a person's page rather than staying alive underneath it, so a
 * plain `rememberSaveable` restart has nothing of its own to restore from.
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
    allFilms: List<MediaSet> = emptyList(),
    onOpenFranchise: (id: Long) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
) {
    val play = remember { FocusRequester() }
    val resume =
        remember(progress) {
            val resumes = progress?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) } != null
            if (resumes) resumeLine(progress) else ""
        }
    val franchise =
        remember(set.setId, set.collectionId, allFilms) {
            set.collectionId?.let { id -> franchisesIn(allFilms).find { it.id == id } }
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
    // See the class doc: restoreKey, not rememberSaveable, is what survives.
    val initialTab =
        remember(tabs, credits, similar, restoreKey) {
            when {
                restoreKey == null -> 0
                credits.cast.any { it.personId.toString() == restoreKey } -> tabs.indexOf("Cast")
                similar.any { it.setId == restoreKey } -> tabs.indexOf("Similar")
                else -> 0
            }
        }
    var selected by rememberSaveable(set.setId) { mutableIntStateOf(initialTab) }
    // Credits and Similar arrive after the page does — on the way back from a person or a
    // title they are still empty at first, so [initialTab] starts at 0 and the saved state
    // keeps it. Once they land and name what was opened, switch to its tab.
    LaunchedEffect(initialTab) { if (restoreKey != null && initialTab > 0) selected = initialTab }
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
                    TvTabBody(TvTitlePageBodyTag) {
                        TvCastRow(credits, onOpenPerson, shouldRequestPortrait, fetchPortrait, restoreKey)
                    }

                "Similar" ->
                    TvTabBody(TvTitlePageBodyTag) {
                        TvSimilarFilms(similar, onOpenTitle, restoreKey)
                    }

                "Details" ->
                    TvTabBody(TvTitlePageBodyTag) {
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
                        // The franchise a film belongs to — `film-page.js`'s
                        // own "Part of" fact — sits with the genres above it,
                        // before the file's own technical line.
                        franchise?.let { own ->
                            TvTextRow(
                                text = "Part of ${own.name}",
                                onClick = { onOpenFranchise(own.id) },
                                modifier = Modifier.padding(bottom = Spacing.small),
                            )
                        }
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
