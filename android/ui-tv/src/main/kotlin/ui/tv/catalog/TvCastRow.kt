package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.Spacing
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
 * A press opens the person's own page through [onOpenPerson]. Each
 * portrait is fetched lazily and at most once per session
 * ([shouldRequestPortrait]/[fetchPortrait] — `data.PortraitRequestLog`'s own
 * rule): a cast row shown for a title nobody scrolls to never asks for
 * anything.
 */
@Composable
internal fun TvCastRow(
    credits: TitleCredits,
    onOpenPerson: (personId: Long) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
) {
    if (credits.cast.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for (credit in credits.cast) {
            TvCastPlate(
                credit = credit,
                onOpen = { onOpenPerson(credit.personId) },
                shouldRequestPortrait = shouldRequestPortrait,
                fetchPortrait = fetchPortrait,
            )
        }
    }
}

@Composable
private fun TvCastPlate(
    credit: Credit,
    onOpen: () -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
) {
    val portrait = rememberPortrait(credit.personId, credit.portraitPath, shouldRequestPortrait, fetchPortrait)
    TvPlate(
        title = credit.name,
        posterPath = portrait?.let(::File),
        onOpen = onOpen,
        modifier = Modifier.width(CastPlateWidth),
        meta = credit.role,
    )
}
