package player

import data.ProgressPoint
import data.ResumePoint
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import model.episodeLabel

/*
 * The run the open title plays in, as the episode sidebar draws it on the
 * phone and the television alike — one model, so the two surfaces cannot
 * group or mark a season differently. Pure: the flow that keeps it current
 * is `EpisodeListFlow.kt`.
 */

/** One title of the run, as one sidebar row. */
data class EpisodeRow(
    val setId: String,
    /** `S1E4` for an episode, `4` for a lesson — the catalogue's own [episodeLabel]; empty when unnumbered or unknown. */
    val number: String,
    val title: String,
    /** The catalogue's runtime, in whole seconds; `null` when it records none. */
    val runtimeSecs: Int?,
    /** Watched to the end: drawn faint with a ✓, and still playable. */
    val watched: Boolean,
    /** How far in, 0 to 1, for a title started and not finished; `null` otherwise, or with no runtime to measure against. */
    val progress: Float?,
    /** The open title: "Now playing", and nothing to press. */
    val current: Boolean,
)

/** One season, or one section of a course: what the sidebar shows under its header. */
data class EpisodeSection(val title: String, val rows: List<EpisodeRow>)

/** The whole run, opening on [currentSection] — the section holding the open title, or the first. */
data class EpisodeList(val sections: List<EpisodeSection>, val currentSection: Int)

/** Heads what nothing could be placed for — an id the catalogue does not know, an episode with no season — placed last, as on the web. */
const val EPISODES_SECTION = "Episodes"

/** What a row says for an id the catalogue does not know, rather than print the raw id back at the viewer. */
const val UNKNOWN_TITLE = "Unknown title"

/**
 * [run] grouped into sections in run order — the run is already the
 * collection's play order, so nothing is re-sorted, only grouped — with
 * each row marked against [watch], one row per id (both surfaces key rows
 * by it). `null` for no run, and for an open title
 * the catalogue knows to belong to no show: a film played from a hand-picked
 * list still walks that list with ⏮/⏭, but it is no series or course to list.
 *
 * Progress is the web's own rule (`progressRuleFor`): any recorded position
 * against a known runtime, so the sidebar and the season page agree about
 * the same row — a watched row with a position left draws the tick and the line.
 */
fun episodeListOf(openId: String, run: List<String>, sets: Map<String, MediaSet>, watch: WatchSnapshot): EpisodeList? {
    if (run.isEmpty()) return null
    if (sets[openId]?.let { it.show == null } == true) return null
    val watched = watch.watched.mapTo(HashSet()) { it.setId }
    val positions = watch.progress.associateBy { it.setId }
    val grouped = LinkedHashMap<List<String>?, MutableList<EpisodeRow>>()
    for (id in run.distinct()) {
        val set = sets[id]
        val done = id in watched
        val progress = ResumePoint.watchedFraction(positions[id]?.let { ProgressPoint(it.at, it.duration) })
        val row = EpisodeRow(
            setId = id,
            number = set?.let(::episodeLabel).orEmpty(),
            title = set?.title ?: UNKNOWN_TITLE,
            runtimeSecs = set?.durationSecs,
            watched = done,
            progress = progress?.toFloat(),
            current = id == openId,
        )
        grouped.getOrPut(set?.let(::sectionOf)) { mutableListOf() } += row
    }
    val placed = grouped.mapNotNull { (trail, rows) -> trail?.let { EpisodeSection(it.last(), rows) } }
    val unplaced = grouped[null]?.let { EpisodeSection(EPISODES_SECTION, it) }
    val sections = placed + listOfNotNull(unplaced)
    val current = sections.indexOfFirst { section -> section.rows.any(EpisodeRow::current) }
    return EpisodeList(sections, current.coerceAtLeast(0))
}

/**
 * The folder trail [set] sits under, as the catalogue shelves it
 * (`Shelves.kt`'s `trailOf`, which this module may not import), or `null`
 * for an episode with no season — placed last rather than given one. A
 * section is keyed by the whole trail but named by its last folder, as the
 * web names it, so two folders sharing a name stay two sections.
 */
private fun sectionOf(set: MediaSet): List<String>? {
    set.path?.split('/')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }?.let { return it }
    set.chapter?.takeIf(String::isNotBlank)?.let { return listOf(it) }
    if (set.kind == Kind.EPISODE) return set.season?.let { listOf("Season $it") }
    return listOf("Chapter ${set.season ?: 1}")
}
