package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * phone's `CastPanel`.
 *
 * A `LazyRow` rather than a plain one, for two reasons together: the full
 * cast (up to a dozen) is reachable by scrolling instead of being squeezed
 * to nothing past the sixth on a row sized for the screen, and a portrait is
 * only fetched once its own plate actually composes — a title nobody
 * scrolls this far into never asks for the rest of its cast's faces.
 *
 * A press opens the person's own page through [onOpenPerson]. [restoreKey]
 * names the person whose page was just left, if any, so the remote comes
 * back to their own plate; with none, or none still in [credits], the first
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
        crewLine(credits.crew)?.let {
            Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(bottom = Spacing.medium))
        }
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
        LaunchedEffect(focusIndex, restoreKey) {
            listState.scrollToItem(focusIndex)
            focusRequester.requestFocus()
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            itemsIndexed(credits.cast, key = { _, credit -> credit.personId }) { index, credit ->
                TvCastPlate(
                    credit = credit,
                    onOpen = { onOpenPerson(credit.personId) },
                    shouldRequestPortrait = shouldRequestPortrait,
                    fetchPortrait = fetchPortrait,
                    modifier = if (index == focusIndex) Modifier.width(CastPlateWidth).focusRequester(focusRequester) else Modifier.width(CastPlateWidth),
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

/** "Directed by A, B" or "Created by A, B" — matches `cast.js`'s own crew line and the phone's `CastPanel`. */
private fun crewLine(crew: List<Credit>): String? {
    if (crew.isEmpty()) return null
    val verb = if (crew.first().role == "Creator") "Created by" else "Directed by"
    return "$verb " + crew.joinToString(", ") { it.name }
}
