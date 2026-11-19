package catalog

import data.ProgressPoint
import data.ResumePoint
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.ageLabel
import model.clockTime

/**
 * One row of a film's Versions list, and of the chooser asked from a card
 * that plays straight away — `versionRows` in the web's
 * `film-versions-view.js`: the copy's label, its technical line, where this
 * viewer is in it, and its own Play.
 */
data class VersionRow(
    val set: MediaSet,
    val label: String,
    val technical: String,
    /** The second this copy resumes from by the per-cut rule, or `null` for the top. */
    val resumeAt: Double?,
    /** Whether this copy itself was finished. */
    val played: Boolean,
    /** Whether this device holds this copy in full. */
    val held: Boolean,
) {
    /** `Resume at 1:02:03`, `Played`, or nothing. */
    val state: String? get() = resumeAt?.let { "Resume at ${clockTime(it)}" } ?: "Played".takeIf { played }

    /** The row's own button. */
    val action: String get() = if (resumeAt != null) "Resume" else "Play"
}

/** [film]'s versions as rows, in version order. */
fun versionRowsOf(
    film: MediaSet,
    watch: WatchSnapshot,
    heldIds: Set<String>,
): List<VersionRow> {
    val positions = watch.progress.associateBy(Progress::setId)
    val watchedAt = watch.watched.associate { it.setId to it.finishedAt }
    return versionsOf(film).map { version ->
        val row = versionPosition(version, film, positions::get, watchedAt::get)
        VersionRow(
            set = version,
            label = versionLabel(version),
            technical = versionTechnicalLine(version),
            resumeAt = ResumePoint.resumeAt(row?.let { ProgressPoint(it.at, it.duration) }),
            played = version.setId in watchedAt,
            held = version.setId in heldIds,
        )
    }
}

/**
 * A film page's line under its title. A film held more than once leaves its
 * runtime to the rows, where it is what tells copies apart.
 */
fun filmHeaderFacts(set: MediaSet): String? =
    factsLine(set.year, set.durationSecs.takeIf { set.versions.isEmpty() }, set.ageLabel(), set.genres)

/**
 * A film page's Details tab: the file facts, or for a film held more than
 * once each copy's facts under its label — `details` in `film-page.js`.
 */
fun filmDetailSections(set: MediaSet): List<Pair<String?, List<FactRow>>> =
    if (set.versions.isEmpty()) listOf(null to filmDetailFacts(set)) else set.versions.map { versionLabel(it) to filmDetailFacts(it) }

/** How many copies a film held more than once offers, for its card — `versionsBadge` in `set-badge.js`; nothing for a title held once. */
fun versionsBadgeOf(set: MediaSet): String? = set.versions.size.takeIf { it > 1 }?.let { "$it versions" }
