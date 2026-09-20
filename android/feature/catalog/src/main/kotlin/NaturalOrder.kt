package catalog

private val RUNS = Regex("""\d+|\D+""")

/**
 * Orders text the way a person reads the numbers in it: `Season 2` before
 * `Season 10`, and `1. Start` before `10. Anhang`.
 *
 * Plain text order gets both backwards, because it compares `1` against `2`
 * one character at a time and stops there. A course's folders are numbered
 * by whoever made it, so this is the difference between a chapter list in
 * its intended order and one that jumps from 1 to 10 and back to 2.
 *
 * Digit runs are compared by length before value, so no number is ever
 * parsed and none is too long to fit.
 */
internal val NATURAL: Comparator<String> = Comparator { left, right ->
    val a = RUNS.findAll(left).map { it.value }.toList()
    val b = RUNS.findAll(right).map { it.value }.toList()
    var order = 0
    for (i in 0 until minOf(a.size, b.size)) {
        order = compareRuns(a[i], b[i])
        if (order != 0) break
    }
    if (order != 0) order else a.size.compareTo(b.size)
}

private fun compareRuns(left: String, right: String): Int {
    if (!left[0].isDigit() || !right[0].isDigit()) return left.compareTo(right, ignoreCase = true)
    val a = left.trimStart('0')
    val b = right.trimStart('0')
    return if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
}
