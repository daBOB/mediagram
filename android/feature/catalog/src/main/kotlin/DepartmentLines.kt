package catalog

import java.text.NumberFormat

/**
 * A department hero's own figures line, spelled the way the web's own
 * `countOf` (`format.js:170-174`) does throughout — a count of twenty or
 * fewer reads as a word, everything past it as figures. Shared by the
 * tablet's department screens and the television's department pages, so a
 * count reads the same word or figure on both: before this, only
 * [documentariesDepartmentOf]'s own line spelled at all, and Movies',
 * Series'/Tutorials', Anime's and Collections' each built their own
 * figures-only line with `ui.catalog.countOf` (ui-mobile) instead.
 */
fun moviesLineOf(department: MoviesDepartment): String =
    listOfNotNull(
        spelledCountOf(department.filmCount, Department.MOVIES.extent),
        department.hours.takeIf { it > 0 }?.let { "${NumberFormat.getIntegerInstance().format(it)} hours" },
    ).joinToString(" · ")

/** "N shows · M episodes" ("four courses · 37 lessons") — counted in [dept]'s own extent and noun. */
fun showsLineOf(
    department: ShowsDepartment,
    dept: Department,
): String =
    listOfNotNull(
        spelledCountOf(department.showCount, dept.extent),
        dept.noun?.let { spelledCountOf(department.itemCount, it) },
    ).joinToString(" · ")

/** "N shows · M films", either half dropped while its own count is zero. */
fun animeLineOf(department: AnimeDepartment): String =
    listOfNotNull(
        department.showCount.takeIf { it > 0 }?.let { spelledCountOf(it, "show") },
        department.filmCount.takeIf { it > 0 }?.let { spelledCountOf(it, "film") },
    ).joinToString(" · ")

/** "N documentaries" — already spelled before this file existed; kept here so every department's line lives beside the others. */
fun documentariesLineOf(department: DocumentariesDepartment): String = spelledCountOf(department.itemCount, Department.DOCUMENTARIES.extent)

/** "N franchises · M lists", the franchise half dropped while there are none. */
fun collectionsLineOf(
    franchiseCount: Int,
    listCount: Int,
): String =
    listOfNotNull(
        franchiseCount.takeIf { it > 0 }?.let { spelledCountOf(it, "franchise") },
        spelledCountOf(listCount, "list"),
    ).joinToString(" · ")
