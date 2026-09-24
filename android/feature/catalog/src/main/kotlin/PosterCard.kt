package catalog

import data.ProgressPoint
import data.ResumePoint
import model.Progress

/** A plate's progress rule from a raw position — shared by every screen that draws a mark from [model.WatchSnapshot]. */
fun watchedFractionOf(progress: Progress?): Float? =
    ResumePoint.watchedFraction(progress?.let { ProgressPoint(it.at, it.duration) })?.toFloat()

private val WHITESPACE = Regex("\\s+")

/** Two letters to stand in for artwork that is not there. */
fun initialsOf(title: String): String =
    title
        .split(WHITESPACE)
        .take(2)
        .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }
