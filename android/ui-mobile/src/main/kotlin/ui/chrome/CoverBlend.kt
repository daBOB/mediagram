package ui.chrome

/**
 * How solid the departments bar should be over Home's own cover, read from
 * where the page actually is rather than tracked scroll deltas: past the
 * cover item entirely (`firstVisibleItemIndex > 0`) is fully solid; inside
 * it, solid once the cover's own bottom edge has scrolled up past the bar's
 * own bottom edge — [coverHeightPx] is the cover item's own measured size
 * (`LazyListState.layoutInfo`'s own answer, not a guess at it from the
 * viewport), so this tracks the cover's real height on every width class
 * and orientation rather than a fraction of the viewport that only happened
 * to be close to it once.
 */
internal fun coverBlend(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    coverHeightPx: Float,
    barHeightPx: Float,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    val threshold = (coverHeightPx - barHeightPx).coerceAtLeast(1f)
    return (firstVisibleItemScrollOffset / threshold).coerceIn(0f, 1f)
}
