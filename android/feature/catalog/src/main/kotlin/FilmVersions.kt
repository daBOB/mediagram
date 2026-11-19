package catalog

import data.ProgressPoint
import data.ResumePoint
import model.Kind
import model.MediaSet
import model.Progress
import model.humanSize

/*
 * One film held as more than one file — a port of the web's
 * `film-versions.js`, which is the reference: the shared fixtures under
 * `web/test/fixtures/film-versions/` pin both surfaces to the same rules.
 *
 * Copies are grouped on read by the poster key the catalog already carries
 * (`tmdb-movie-<id>`), so the index, the watch state and the sync record stay
 * one row per file. Copies of one cut share a timeline, so a position carries
 * between them; cuts do not, so it never crosses from one to another.
 */

/** Best first; anything else sorts after these. */
private val RESOLUTIONS = listOf("2160p", "1080p", "720p", "480p")

private fun resolutionRank(quality: String?): Int = RESOLUTIONS.indexOf(quality).let { if (it == -1) RESOLUTIONS.size else it }

private fun isHdr(set: MediaSet): Boolean = !set.hdr.isNullOrEmpty() && set.hdr != "SDR"

/** A version's cut; `null` is the standard one, and an empty one is too. */
fun cutOf(set: MediaSet): String? = set.variant?.ifEmpty { null }

/**
 * Version order: the standard cut first, other cuts by name (plain code-unit
 * order, never the locale's, so both surfaces sort alike); within a cut the
 * higher resolution, then HDR before SDR; then the set id, so the order is
 * total and two devices never disagree about which copy comes first.
 */
val VERSION_ORDER: Comparator<MediaSet> =
    Comparator { a, b ->
        val ca = cutOf(a)
        val cb = cutOf(b)
        when {
            ca != cb && ca == null -> -1
            ca != cb && cb == null -> 1
            ca != cb -> ca!!.compareTo(cb!!)
            else ->
                compareValuesBy(a, b, { resolutionRank(it.quality) }, { !isHdr(it) }, MediaSet::setId)
        }
    }

/**
 * A film's identity: its TMDB poster key, or the set itself. Only films with a
 * TMDB id group — a `title-<slug>` key ignores the year, and two untagged
 * films of one name are two films.
 */
private fun filmKeyOf(set: MediaSet): String =
    set.posterKey?.takeIf { set.kind == Kind.MOVIE && it.startsWith("tmdb-movie-") } ?: "set:${set.setId}"

/**
 * One entry per film, in the order each film first appears.
 *
 * A film held once is its set, untouched, so a single copy renders exactly as
 * it always has. A film held more than once is its first version in version
 * order — the representative: poster, title, facts — carrying every version
 * in [MediaSet.versions].
 */
fun groupFilms(sets: List<MediaSet>): List<MediaSet> =
    sets.groupBy(::filmKeyOf).values.map { copies ->
        if (copies.size == 1) copies.single() else copies.sortedWith(VERSION_ORDER).let { it.first().copy(versions = it) }
    }

/** Every version of a film, in version order; a single copy is its own. */
fun versionsOf(film: MediaSet): List<MediaSet> = film.versions.ifEmpty { listOf(film) }

/** Every version's set id to the film it belongs to — films held more than once only. */
fun filmIndexOf(films: Sequence<MediaSet>): Map<String, MediaSet> =
    buildMap { for (film in films) if (film.versions.isNotEmpty()) for (version in film.versions) put(version.setId, film) }

/**
 * How a version is told apart from its siblings:
 * `Standard · 4K HDR · 3h 1m · 14 GB`. Runtime is always there, because it
 * is how two cuts differ; parts the index does not know are left out.
 */
fun versionLabel(set: MediaSet): String {
    val resolution = if (set.quality == "2160p") "4K" else set.quality
    val picture = listOfNotNull(resolution, "HDR".takeIf { isHdr(set) }).filter(String::isNotEmpty).joinToString(" ")
    val size = set.totalBytes.takeIf { it > 0 }?.let(::humanSize)
    return listOfNotNull(cutOf(set) ?: "Standard", picture, humanDuration(set.durationSecs), size)
        .filter(String::isNotEmpty)
        .joinToString(" · ")
}

/** `mkv · av1 · aac · German, English`: what a row needs to tell two copies apart. */
fun versionTechnicalLine(set: MediaSet): String =
    listOfNotNull(set.container, set.vcodec, set.acodec, set.alang.joinToString(", ", transform = ::languageName))
        .filter(String::isNotEmpty)
        .joinToString(" · ")

/** A film is played once any of its versions has been finished. */
fun filmPlayed(
    film: MediaSet,
    watchedAt: (String) -> Long?,
): Boolean = versionsOf(film).any { watchedAt(it.setId) != null }

/** Every copy of [film] sharing [version]'s cut. */
private fun sameCut(
    version: MediaSet,
    film: MediaSet,
): List<MediaSet> = versionsOf(film).filter { cutOf(it) == cutOf(version) }

/** Whether a copy among [copies] was finished after [row] was saved. */
private fun closedBy(
    copies: List<MediaSet>,
    row: Progress,
    watchedAt: (String) -> Long?,
): Boolean = copies.any { (watchedAt(it.setId) ?: Long.MIN_VALUE) > row.updatedAt }

/**
 * The position [version] starts from, as a progress row, or `null`.
 *
 * The newest position saved in any version of the same cut, unless a version
 * of that cut was finished after it was saved: finishing the 4K copy closes
 * the half-watched 1080p one too, while a Director's Cut keeps its own place.
 * Thresholds ([ResumePoint.resumeAt]) are the caller's, as for any single title.
 */
fun versionPosition(
    version: MediaSet,
    film: MediaSet,
    progressOf: (String) -> Progress?,
    watchedAt: (String) -> Long?,
): Progress? {
    val copies = sameCut(version, film)
    val newest = copies.mapNotNull { progressOf(it.setId) }.maxByOrNull(Progress::updatedAt) ?: return null
    return newest.takeUnless { closedBy(copies, it, watchedAt) }
}

/** The film's newest resumable position across all its cuts, for its card. */
fun filmPosition(
    film: MediaSet,
    progressOf: (String) -> Progress?,
    watchedAt: (String) -> Long?,
): Progress? =
    versionsOf(film)
        .mapNotNull { versionPosition(it, film, progressOf, watchedAt) }
        .filter { resumable(it) }
        .maxByOrNull(Progress::updatedAt)

private fun resumable(row: Progress): Boolean = ResumePoint.resumeAt(ProgressPoint(row.at, row.duration)) != null

/**
 * Progress rows (newest first) as Continue should see them. A film held more
 * than once keeps one row: the newest that is its copy's own position by the
 * per-cut rule ([versionPosition]) and resumable — so a newer glance at one
 * copy hides an older place in another of that cut, as the chooser's rows and
 * the card already say. Rows for anything held once pass untouched, so
 * episodes and single films keep the rules they always had.
 */
fun continueRows(
    rows: List<Progress>,
    filmOf: (String) -> MediaSet?,
    watchedAt: (String) -> Long?,
): List<Progress> {
    val bySet = rows.associateBy(Progress::setId)
    val shown = HashSet<String>()
    return rows.filter { row ->
        val film = filmOf(row.setId)?.takeIf { it.versions.isNotEmpty() } ?: return@filter true
        // Every caller drops an unresumable row anyway.
        if (!resumable(row)) return@filter true
        val version = film.versions.first { it.setId == row.setId }
        versionPosition(version, film, bySet::get, watchedAt) == row && shown.add(film.setId)
    }
}

/**
 * Sets (a list, the watchlist, search hits) with each copy of a film held more
 * than once standing in as its whole film, once. Anything else is kept as it
 * came.
 */
fun asFilms(
    items: List<MediaSet>,
    filmOf: (String) -> MediaSet?,
): List<MediaSet> {
    val seen = HashSet<String>()
    return items.mapNotNull { item ->
        val shown = filmOf(item.setId)?.takeIf { it.versions.isNotEmpty() } ?: item
        shown.takeIf { seen.add(it.setId) }
    }
}
