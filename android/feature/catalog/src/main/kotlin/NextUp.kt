package catalog

import data.ProgressPoint
import data.ResumePoint
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/** What one show or course offers the viewer, and when it was last touched. */
data class NextUpEntry(val set: MediaSet, val resume: Boolean, val touchedAt: Long)

/**
 * Continue and Next up, before [homeRowsOf] turns them into rows with
 * captions. Ported from the underway half of `home-shelves.js`'s
 * `homeShelves` — [homeRowsOf] owns the rest: captions, the Latest shelves,
 * and wrapping this in [HomeRow].
 */
data class Underway(
    val continues: List<MediaSet>,
    val nextUp: List<NextUpEntry>,
    val continuesTotal: Int,
    val nextUpTotal: Int,
)

/**
 * [collections] underway, newest touch first, split into Continue and
 * Next up. [byId] resolves a progress row to the set it belongs to — a film
 * as much as an episode, since Continue holds both.
 */
fun underwayOf(
    collections: List<Entry.Collection>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
    limit: Int,
): Underway {
    val positions = watch.progress.associateBy { it.setId }
    val watchedAt = watch.watched.associate { it.setId to it.finishedAt }

    val underway = collections
        .mapNotNull { nextInCollection(playOrder(it.divisions), positions) { id -> watchedAt[id] } }
        .sortedByDescending(NextUpEntry::touchedAt)
    val nextUp = underway.take(limit)
    // One title is one card on this page: a show being watched would
    // otherwise appear twice — as the episode in progress, and again under
    // Next up.
    val shown = nextUp.mapTo(HashSet()) { it.set.setId }

    val started = watch.progress
        .sortedByDescending(Progress::updatedAt)
        .filter { ResumePoint.resumeAt(it.toProgressPoint()) != null }
        .mapNotNull { byId[it.setId] }

    return Underway(
        continues = started.filterNot { it.setId in shown }.take(limit),
        nextUp = nextUp,
        // Every started title, including the ones this page moved to Next
        // up: the figure has to agree with what "See all" opens.
        continuesTotal = started.size,
        nextUpTotal = underway.size,
    )
}

/**
 * What to offer from one show or course, or `null` when it offers nothing.
 * Ported from `nextInCollection` in home-shelves.js — see that file for the
 * four cases this walks through.
 */
fun nextInCollection(
    order: List<MediaSet>,
    positions: Map<String, Progress>,
    watchedAt: (String) -> Long?,
): NextUpEntry? {
    var resumeSet: MediaSet? = null
    var resumeTouch: Long? = null
    var finishedSet: MediaSet? = null
    var finishedTouch: Long? = null

    for (set in order) {
        val row = positions[set.setId]
        if (row != null &&
            ResumePoint.resumeAt(row.toProgressPoint()) != null &&
            (resumeTouch == null || row.updatedAt > resumeTouch)
        ) {
            resumeTouch = row.updatedAt
            resumeSet = set
        }
        val finished = watchedAt(set.setId)
        if (finished != null && (finishedTouch == null || finished > finishedTouch)) {
            finishedTouch = finished
            finishedSet = set
        }
    }

    val touchedAt = laterOf(resumeTouch, finishedTouch) ?: return null
    if (resumeSet != null) return NextUpEntry(resumeSet, resume = true, touchedAt = touchedAt)

    // Walked forward from the one finished most recently rather than
    // searched from the start, so an episode seen out of order does not
    // pull the show backwards.
    var candidate = nextAfter(order, requireNotNull(finishedSet).setId)
    while (candidate != null && watchedAt(candidate.setId) != null) {
        candidate = nextAfter(order, candidate.setId)
    }
    return candidate?.let { NextUpEntry(it, resume = false, touchedAt = touchedAt) }
}

private fun laterOf(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

/** What follows [setId] in [order], or `null` at the end. Ported from `nextInQueue`/`nextAfter` in library.js. */
fun nextAfter(order: List<MediaSet>, setId: String): MediaSet? {
    val at = order.indexOfFirst { it.setId == setId }
    return if (at == -1 || at == order.lastIndex) null else order[at + 1]
}

/**
 * Every playable set in a collection, in the order Android's own screens
 * render it: a level's lessons and folders interleaved by their leading
 * number, the way `library.js:182-196` already orders the web's pages (Q6).
 * A document is skipped rather than descended into — it has nothing below
 * it, and "next" means the next thing that plays.
 */
fun playOrder(divisions: List<Division>): List<MediaSet> {
    val out = mutableListOf<MediaSet>()
    fun descend(items: List<MediaSet>, children: List<Division>) {
        for (entry in levelEntries(items, children)) {
            val lesson = entry.lesson
            val folder = entry.folder
            when {
                lesson != null -> if (lesson.kind != Kind.DOCUMENT) out.add(lesson)
                folder != null -> descend(folder.items, folder.children)
            }
        }
    }
    descend(emptyList(), divisions)
    return out
}

private data class LevelEntry(val order: Int?, val lesson: MediaSet?, val folder: Division?)

/**
 * One level's lessons and folders, in the order the course puts them —
 * ported from `levelEntries` in library.js. A lesson's number is its own
 * leading episode number; a folder's is the number its own name leads with,
 * e.g. "14. Exkurs TWS" sorts as 14. Anything unnumbered sorts to the end,
 * lessons before folders — [sortedBy] is stable, so each keeps the order it
 * already arrived in.
 */
private fun levelEntries(items: List<MediaSet>, children: List<Division>): List<LevelEntry> {
    val lessons = items.map { LevelEntry(it.episodeFirst, it, null) }
    val folders = children.map { LevelEntry(leadingNumber(it.title), null, it) }
    return (lessons + folders).sortedBy { it.order ?: Int.MAX_VALUE }
}

private val LEADING_NUMBER = Regex("""^\s*(\d+)""")

/** The number a folder's name leads with, or `null` for neither — ported from `leadingNumber` in library.js. */
private fun leadingNumber(text: String?): Int? =
    text?.let { LEADING_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }

private fun Progress.toProgressPoint(): ProgressPoint = ProgressPoint(at, duration)
