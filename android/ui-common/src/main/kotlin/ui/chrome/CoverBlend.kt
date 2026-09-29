package ui.chrome

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * How solid the departments bar should be over whatever the first item of
 * the tab under it draws — Home's own cover, or a department's own hero,
 * both a big picture up top that the bar starts translucent over — read
 * from where the page actually is rather than tracked scroll deltas: past
 * that item entirely (`firstVisibleItemIndex > 0`) is fully solid; inside
 * it, solid once its own bottom edge has scrolled up past the bar's own
 * bottom edge — [coverHeightPx] is that item's own measured size
 * (`LazyListState`/`LazyGridState`'s own `layoutInfo`, not a guess at it
 * from the viewport), so this tracks the real height on every width class
 * and orientation rather than a fraction of the viewport that only happened
 * to be close to it once.
 *
 * Shared by the tablet's own hero-bleeding pages and the television's Home,
 * over its own magazine cover — both read the same live measurement rather
 * than each tracking its own scroll delta.
 */
fun coverBlend(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    coverHeightPx: Float,
    barHeightPx: Float,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    val threshold = (coverHeightPx - barHeightPx).coerceAtLeast(1f)
    return (firstVisibleItemScrollOffset / threshold).coerceIn(0f, 1f)
}

/**
 * What [coverBlend] needs from whichever list sits under a bleeding hero —
 * read live so a caller can ask for this once per list and hand the same
 * instance to a `derivedStateOf` on every scroll frame, rather than a plain
 * snapshot of numbers taken once. `null` where a tab draws no hero at all
 * (a kept wall, Collections, a plain shelf) — the bar reads that as "start
 * solid", the same as a hero that turned out to have no lead art.
 */
interface HeroListState {
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int

    /** The hero item's own measured height once it has been laid out, `0f` before that. */
    val heroHeightPx: Float
}

/** [HeroListState] over a [LazyListState] — Movies, Documentaries, the television's Home: the hero is item 0 of a plain column. */
fun LazyListState.asHeroListState(): HeroListState =
    object : HeroListState {
        override val firstVisibleItemIndex get() = this@asHeroListState.firstVisibleItemIndex
        override val firstVisibleItemScrollOffset get() = this@asHeroListState.firstVisibleItemScrollOffset
        override val heroHeightPx get() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: 0f
    }

/** [HeroListState] over a [LazyGridState] — Series, Tutorials: the hero is item 0, spanning every column. */
fun LazyGridState.asHeroListState(): HeroListState =
    object : HeroListState {
        override val firstVisibleItemIndex get() = this@asHeroListState.firstVisibleItemIndex
        override val firstVisibleItemScrollOffset get() = this@asHeroListState.firstVisibleItemScrollOffset
        override val heroHeightPx get() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.height?.toFloat() ?: 0f
    }
