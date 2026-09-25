package catalog

/**
 * Pages of a long shelf — `pager.js` in the web player, ported. The Movies
 * shelf is drawn one page at a time; the library is already whole on the
 * device, so this only chooses which slice to draw.
 */

/**
 * Films on one page: a multiple of two, three, four, six and eight, so a wall
 * of plates ends on a full row at any column count — the web's own number.
 */
const val FILMS_PER_PAGE = 48

/** One page of a shelf, with the page number brought into range. */
data class Page<T>(val items: List<T>, val page: Int, val pages: Int)

/** Page [page] of [items]; before the first is the first, past the last is the last. */
fun <T> pageOf(
    items: List<T>,
    page: Int,
    size: Int = FILMS_PER_PAGE,
): Page<T> {
    val pages = maxOf(1, (items.size + size - 1) / size)
    val current = page.coerceIn(1, pages)
    val start = (current - 1) * size
    return Page(items.subList(start, minOf(start + size, items.size)), current, pages)
}

/** One entry in the row of page links: a page number, or a gap where pages are left out. */
sealed interface PageLink {
    data class To(val page: Int) : PageLink

    data object Gap : PageLink
}

/** The longest the row gets with gaps: both ends, three around the current page and two gaps. */
private const val FITS = 7

/**
 * The page numbers the pager shows — `pageLinks` in `pager.js`: both ends,
 * the current page and its neighbours, with a gap wherever pages are left
 * out. A gap that would hide a single page shows that page instead, since a
 * gap is no shorter. Nothing at all for a single page.
 */
fun pageLinks(
    current: Int,
    pages: Int,
): List<PageLink> {
    if (pages <= 1) return emptyList()
    if (pages <= FITS) return (1..pages).map(PageLink::To)
    val shown = listOf(1, current - 1, current, current + 1, pages).filter { it in 1..pages }.distinct().sorted()
    val links = mutableListOf<PageLink>()
    for (page in shown) {
        val previous = (links.lastOrNull() as? PageLink.To)?.page
        if (previous != null && page - previous == 2) links += PageLink.To(previous + 1)
        if (previous != null && page - previous > 2) links += PageLink.Gap
        links += PageLink.To(page)
    }
    return links
}
