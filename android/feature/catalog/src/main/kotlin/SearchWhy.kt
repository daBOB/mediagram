package catalog

/**
 * Why a hit earned its place, in words rather than a field name — ported
 * from `search-view.js`'s `WHY`. `"title"`, and anything this build does not
 * recognise, say nothing: a title match is what a viewer expects a search
 * to do, so it is the one reason not worth naming.
 */
private val WHY = mapOf(
    "show" to "matched the series or course",
    "chap" to "matched the chapter",
    "path" to "matched the folder",
    "summary" to "found in the summary",
)

/** The badge text for [matched], or `null` when the hit needs no explaining. */
fun searchWhy(matched: String): String? = WHY[matched]
