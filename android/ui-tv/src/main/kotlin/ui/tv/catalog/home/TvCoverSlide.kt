package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.coverFactsLine
import designsystem.Backdrop
import designsystem.CoverTitle
import designsystem.Eyebrow
import designsystem.LocalBackdrop
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import ui.catalog.CoverScrim
import ui.catalog.HeroArtwork
import ui.tv.chrome.TvDepartmentsBarHeight

/** Past this many characters the title steps down a size, the phone cover's own rule (`HOME_COVER`, `CoverSlide.kt`). */
private const val LONG_TITLE = 18

/**
 * How far below the screen's own top edge the cover's own words are ever
 * allowed to start — the bar's full drawn height (its own row plus the
 * overscan margin above it), plus a little breathing room. The cover's own
 * *picture* still bleeds all the way to the top, under the bar, on
 * purpose; only the words themselves are kept clear of it.
 *
 * Read on the words column's own top padding below, not on [TvHomeCover]'s
 * `coverHeight` floor: that floor sizes the cover for the common case, but
 * a long title, a three-line tagline and the facts line together can still
 * ask for more room than the floor leaves under the bar — the words column
 * is `Alignment.BottomStart`-aligned within a `Box` whose own height then
 * grows to fit them, and without a top padding of its own the words simply
 * start at that (taller) box's own top edge, which is the screen's own
 * y=0, squarely behind the bar. A `top` padding this size is what actually
 * guarantees the invariant, regardless of exactly how tall the words turn
 * out to be — see `TvCoverSlideBarClearanceTest`.
 */
internal val TvHomeBarClearance = TvDepartmentsBarHeight + Overscan.vertical + Spacing.medium

/**
 * One cover story's own picture, headline and facts over its own backdrop —
 * the television twin of the phone's `CoverSlide`, minus the action row and
 * dots: [TvHomeCover] draws those outside this composable's own `Crossfade`
 * so neither ever fades with the picture (a viewer's finger — the remote's
 * focus, here — must never sit on a control mid-fade for a film no longer
 * shown).
 *
 * [minHeight] is a floor, not the height itself, the same
 * `heightIn(min = ...)` reason the phone's own slide gives: nothing here
 * ever asks a child to fill the unbounded height a `LazyColumn` item offers.
 * Home's own cover keeps its picture under every [Backdrop] but
 * [Backdrop.SOLID] — [LocalBackdrop] is read directly rather than taking a
 * `departmentHero` flag the way [ui.tv.catalog.TvCoverStory] does, since
 * this composable never draws a department's own hero.
 */
@Composable
internal fun TvCoverSlide(
    set: MediaSet,
    minHeight: Dp,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)) {
        val art = set.backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
        if (art != null) {
            HeroArtwork(path = art, modifier = Modifier.matchParentSize())
        } else {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        CoverScrim(compact = false, paper = MaterialTheme.colorScheme.background, modifier = Modifier.matchParentSize())

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = CoverTextMaxWidth)
                    .padding(
                        start = Spacing.extraLarge,
                        end = Spacing.extraLarge,
                        // See `TvHomeBarClearance`'s own doc: this is what
                        // actually keeps the words clear of the bar when
                        // they need more room than the cover's own floor
                        // height reserves — bottom-alignment alone only
                        // ever pads the *unused* space above short content;
                        // once the words are tall enough to fill the whole
                        // box, their own top IS the box's top, which the
                        // bar draws straight over without this.
                        top = TvHomeBarClearance,
                        bottom = CoverActionsReservedHeight,
                    ),
        ) {
            val genre = set.genres.firstOrNull()
            Text(
                text = listOfNotNull("Featured today", genre).joinToString(" · ").uppercase(),
                style = Eyebrow.copy(fontSize = TvTypeScale.eyebrow),
                color = OnImage2,
                modifier = Modifier.padding(bottom = Spacing.medium),
            )
            val long = set.title.length > LONG_TITLE
            Text(
                text = set.title.uppercase(),
                style = CoverTitle.copy(fontSize = if (long) 42.sp else 61.sp, lineHeight = if (long) 0.9.em else CoverTitle.lineHeight),
                color = OnImage,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            set.tagline?.takeIf(String::isNotEmpty)?.let { tagline ->
                Text(
                    text = tagline,
                    style = TvTypeScale.body,
                    color = OnImage2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.large),
                )
            }
            coverFactsLine(set)?.let { meta ->
                Text(
                    text = meta,
                    style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
                    color = OnImage2,
                    modifier = Modifier.padding(top = Spacing.small),
                )
            }
        }
    }
}

/**
 * Type set over artwork keeps the same two colours in both themes — the
 * web's `--on-image`/`--on-image-2` (`theme.css:100-101`), the phone
 * cover's own `OnImage`/`OnImage2`. Redeclared here rather than shared:
 * both are `internal` to ui-mobile's own module, and lifting two colour
 * constants across a module boundary for this alone was not worth the
 * coupling — see this phase's own report for the calls it did lift.
 */
internal val OnImage = androidx.compose.ui.graphics.Color(0xFFF6F2EA)
internal val OnImage2 = androidx.compose.ui.graphics.Color(0xD1F6F2EA)

/** The cover's own text column never runs wall-to-wall even on a 960dp screen — the phone cover's own `textMaxWidth` cap, at TV's one width. */
private val CoverTextMaxWidth = 620.dp

/** Room left under the words for [TvCoverActions]' own row and dots, drawn as a further sibling outside the crossfade — see this file's own doc. */
internal val CoverActionsReservedHeight = 96.dp
