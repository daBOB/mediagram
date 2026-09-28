package catalog

/**
 * The row every uncategorised unit falls into — a line-for-line port of the
 * web's `categoryRows` (`web/public/lib/categories.js`), held to the same
 * fixture ([CategoryRowsFixtureTest]).
 */
const val OTHER_CATEGORY = "Other"

/** One row of a department's category strip: every unit sharing [title]. */
data class CategoryRow<T>(val title: String, val units: List<T>)

/**
 * One row per hand-set category found in [units], "Other" last, omitted
 * when empty; `[]` when nothing in [units] is categorised, so a department
 * page draws exactly as it did before any unit was ever filed.
 *
 * `groupBy` keeps a [LinkedHashMap] under the hood, so a row's own units
 * stay in [units]' order — the same "input order kept" rule the web's
 * `Map`-based grouping follows.
 */
fun <T> categoryRowsOf(units: List<T>, categoryOf: (T) -> String?): List<CategoryRow<T>> {
    if (units.none { categoryOf(it) != null }) return emptyList()
    return units.groupBy { categoryOf(it) ?: OTHER_CATEGORY }
        .map { (title, members) -> CategoryRow(title, members) }
        .sortedWith(compareBy<CategoryRow<T>> { it.title == OTHER_CATEGORY }.thenBy(NATURAL) { it.title })
}

/**
 * The category a department-page card stands for: a film's own, or a
 * collection's first item's — the same unit a category is filed under in
 * the first place (`mlib_spec::category_key::category_key`'s own doc).
 */
fun categoryOf(entry: Entry): String? =
    when (entry) {
        is Entry.Film -> entry.set.category
        is Entry.Collection -> firstItemOf(entry.divisions)?.category
    }
