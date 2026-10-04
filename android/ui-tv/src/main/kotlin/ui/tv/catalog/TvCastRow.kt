package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.Credit
import model.TitleCredits
import ui.catalog.rememberPortrait
import ui.tv.TvTextRow

/** How wide a cast member's own plate is — narrower than a poster, since a portrait is closer to square. */
private val CastPlateWidth = 130.dp

/**
 * A title's cast, a horizontal row of people rather than a wall — the
 * television twin of the phone's Cast tab and the web's `cast.js`. Nothing
 * is drawn while [credits] carries no cast: gating the Cast tab itself on
 * that is the caller's job, matching `credits.cast.isNotEmpty()` everywhere
 * else this rule is applied.
 *
 * Who directed or created it heads the row, in `cast.js`'s own words:
 * "Directed by A, B" for a film's crew, "Created by A, B" for a show's —
 * told apart by the first credited person's own role, the same as the
 * phone's `CastPanel`. Each name is a stop that opens that person's page,
 * as the web's and the phone's links do: a director is as likely to be
 * what a viewer came for as an actor. The line sits between the tab row
 * and the plates, so Up from a plate reaches it and Up again the tab row,
 * whose own routing lands on the Cast tab.
 *
 * A `LazyRow` rather than a plain one, for two reasons together: the full
 * cast (up to a dozen) is reachable by scrolling instead of being squeezed
 * to nothing past the sixth on a row sized for the screen, and a portrait is
 * only fetched once its own plate actually composes — a title nobody
 * scrolls this far into never asks for the rest of its cast's faces.
 *
 * A press opens the person's own page through [onOpenPerson]. [restoreKey]
 * names the person whose page was just left, if any, so the remote comes
 * back to their own plate — or to their name on the crew line, for someone
 * credited only there; with none, or none still in [credits], the first
 * plate takes focus instead, so this row is never left with nothing focused.
 */
@Composable
internal fun TvCastRow(
    credits: TitleCredits,
    onOpenPerson: (personId: Long) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
    restoreKey: String? = null,
) {
    if (credits.cast.isEmpty()) return
    Column {
        val crewFocus = remember { FocusRequester() }
        // Only someone not also in the cast: a director who acts comes back
        // to their plate, the larger of the two stops.
        val restoredCrew =
            remember(credits, restoreKey) {
                restoreKey
                    ?.takeIf { wanted -> credits.cast.none { it.personId.toString() == wanted } }
                    ?.let { wanted -> credits.crew.indexOfFirst { it.personId.toString() == wanted } }
                    ?.takeIf { it >= 0 }
            }
        TvCrewLine(credits.crew, onOpenPerson, restoredCrew, crewFocus)
        val listState = rememberLazyListState()
        val focusRequester = remember { FocusRequester() }
        val focusIndex =
            remember(credits, restoreKey) {
                restoreKey
                    ?.let { wanted -> credits.cast.indexOfFirst { it.personId.toString() == wanted } }
                    ?.takeIf { it >= 0 } ?: 0
            }
        // Not gated on `LocalTakesArrivalFocus`: that flag says whether the
        // whole page's own arrival stop is this row (the film page's own
        // rule), not whether a tab a viewer just pressed should focus its
        // own content — a series page turns it off outright for any tab
        // but Episodes, which would leave a freshly opened Cast tab with
        // nothing focused at all.
        LaunchedEffect(focusIndex, restoredCrew, restoreKey) {
            if (restoredCrew != null) {
                crewFocus.requestFocus()
            } else {
                listState.scrollToItem(focusIndex)
                focusRequester.requestFocus()
            }
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            itemsIndexed(credits.cast, key = { _, credit -> credit.personId }) { index, credit ->
                // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
                val ownRequester = remember { FocusRequester() }
                TvCastPlate(
                    credit = credit,
                    onOpen = { onOpenPerson(credit.personId) },
                    shouldRequestPortrait = shouldRequestPortrait,
                    fetchPortrait = fetchPortrait,
                    modifier = Modifier.width(CastPlateWidth).focusRequester(if (index == focusIndex) focusRequester else ownRequester),
                )
            }
        }
    }
}

@Composable
private fun TvCastPlate(
    credit: Credit,
    onOpen: () -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
    modifier: Modifier = Modifier,
) {
    val portrait = rememberPortrait(credit.personId, credit.portraitPath, shouldRequestPortrait, fetchPortrait)
    TvPlate(
        title = credit.name,
        posterPath = portrait?.let(::File),
        onOpen = onOpen,
        modifier = modifier,
        meta = credit.role,
    )
}

/**
 * "Directed by A, B" or "Created by A, B" — `cast.js`'s own crew line and
 * the phone's `CastPanel` — each name a [TvTextRow] that opens its person.
 * [focused] is the index of the name whose page was just left, which
 * carries [focus]; every other name keeps a requester of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvCrewLine(
    crew: List<Credit>,
    onOpenPerson: (personId: Long) -> Unit,
    focused: Int?,
    focus: FocusRequester,
) {
    if (crew.isEmpty()) return
    FlowRow(modifier = Modifier.padding(bottom = Spacing.medium), horizontalArrangement = Arrangement.spacedBy(CrewGap)) {
        Text(text = if (crew.first().role == "Creator") "Created by" else "Directed by", style = TvTypeScale.body)
        crew.forEachIndexed { at, person ->
            key(person.personId) {
                // The comma rides with the name before it, so a wrap never starts a line with one.
                Row {
                    TvTextRow(text = person.name, onClick = { onOpenPerson(person.personId) }, focusRequester = focus.takeIf { at == focused })
                    if (at < crew.lastIndex) Text(text = ",", style = TvTypeScale.body)
                }
            }
        }
    }
}

/** A word space at the body size, between "Directed by" and each name. */
private val CrewGap = 8.dp

/** Whether [personKey], a restore key, names anyone on a title's Cast tab — its cast, or the crew line above it. */
internal fun TitleCredits.onCastTab(personKey: String?): Boolean =
    personKey != null && (cast.any { it.personId.toString() == personKey } || crew.any { it.personId.toString() == personKey })
