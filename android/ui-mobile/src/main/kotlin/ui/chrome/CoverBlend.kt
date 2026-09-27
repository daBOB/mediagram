package ui.chrome

/**
 * How solid the departments bar should be over Home's own cover, read from
 * where the page actually is rather than tracked scroll deltas — past the
 * cover item entirely (`firstVisibleItemIndex > 0`) is fully solid; inside
 * it, solid grows from 40% of the viewport scrolled to 75%, the web's own
 * `bar-settle` range (`shell.css`'s `animation-range: 40vh 75vh`).
 */
internal fun coverBlend(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    viewportPx: Float,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    val start = viewportPx * 0.40f
    val end = viewportPx * 0.75f
    return ((firstVisibleItemScrollOffset - start) / (end - start)).coerceIn(0f, 1f)
}
