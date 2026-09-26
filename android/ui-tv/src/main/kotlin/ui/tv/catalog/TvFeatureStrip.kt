package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import catalog.Feature
import catalog.FeatureKind
import designsystem.Spacing
import java.io.File
import model.Kind

/** The label naming the rule that chose the title — the phone's own `labelFor`, ported verbatim. */
private fun labelFor(kind: FeatureKind): String =
    when (kind) {
        FeatureKind.EDITOR -> "Editor's choice"
        FeatureKind.STAFF -> "Staff pick"
        FeatureKind.TRENDING -> "Trending on TMDB"
        FeatureKind.NEW -> "New in the library"
    }

/**
 * The three feature cards under the cover story — the television twin of
 * the phone's `FeatureStrip`, over the same [Feature] picks: each one a
 * [TvPlate] rather than the phone's own bespoke card, so a feature focuses
 * and presses exactly like every other plate on this surface.
 *
 * Every unpinned feature is a film (`MagazineHome`'s own rule), so
 * [onOpenTitle] is the only route a card ever needs.
 */
@Composable
internal fun TvFeatureStrip(
    features: List<Feature>,
    onOpenTitle: (String) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    if (features.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        features.forEachIndexed { index, feature ->
            val set = feature.set
            val series = set.kind == Kind.EPISODE && set.show != null
            val title = if (series) requireNotNull(set.show) else set.title
            val art = set.backdropPath ?: set.posterPath
            TvPlate(
                title = title,
                posterPath = art?.let(::File),
                onOpen = { onOpenTitle(set.setId) },
                modifier =
                    Modifier
                        .weight(1f)
                        .let { m -> if (index == 0 && focusRequester != null) m.focusRequester(focusRequester) else m },
                meta = labelFor(feature.kind),
                caption = set.tagline?.ifEmpty { null },
            )
        }
    }
}
