package catalog

import model.MediaSet
import model.humanSize
import uniffi.mediagram_core.TitleInfo

/**
 * What a show adds up to, from the episodes the catalog already holds —
 * ported from the web's `series-summary.js#summarize`. What the provider
 * says about the show as a whole (air dates, how many episodes exist) is
 * not a fact about the files, so it stays in [TitleInfo] and is handed to
 * the lines that compare against it ([yearLine], [scaleLine]).
 */
data class SeriesFacts(
    val episodes: Int,
    val seasons: Int,
    val runtimeSecs: Int,
    val totalBytes: Long,
    val years: List<Int>,
    val qualities: List<String>,
    val hdr: List<String>,
    val video: List<String>,
    val audio: List<String>,
    val audioLanguages: List<String>,
    val subtitleLanguages: List<String>,
)

private val QUALITY_ORDER = listOf("SD", "480p", "720p", "1080p", "1440p", "2160p")

/** The distinct values [pick] names across [sets], first-seen order kept — mirrors `series-summary.js`'s own `distinct`. */
private fun <T> distinct(
    sets: List<MediaSet>,
    pick: (MediaSet) -> T?,
): List<T> {
    val seen = LinkedHashSet<T>()
    for (set in sets) pick(set)?.let(seen::add)
    return seen.toList()
}

private fun <T> distinctAll(
    sets: List<MediaSet>,
    pick: (MediaSet) -> List<T>,
): List<T> {
    val seen = LinkedHashSet<T>()
    for (set in sets) seen.addAll(pick(set))
    return seen.toList()
}

fun summarize(divisions: List<Division>): SeriesFacts {
    val sets = divisions.flatMap { it.walk() }.flatMap { it.items }
    val qualities =
        distinct(sets) { it.quality }.sortedBy { q ->
            QUALITY_ORDER.indexOf(q).let { if (it < 0) QUALITY_ORDER.size else it }
        }
    return SeriesFacts(
        episodes = sets.size,
        seasons = divisions.size,
        runtimeSecs = sets.sumOf { it.durationSecs ?: 0 },
        totalBytes = sets.sumOf { it.totalBytes },
        years = distinct(sets) { it.year?.takeIf { y -> y > 0 } }.sorted(),
        qualities = qualities,
        // SDR is the absence of anything worth saying, so it only counts once some episode has more than it.
        hdr = distinct(sets) { it.hdr?.takeIf { h -> h != "SDR" } },
        video = distinct(sets) { it.vcodec },
        audio = distinct(sets) { it.acodec },
        audioLanguages = distinctAll(sets) { it.alang },
        subtitleLanguages = distinctAll(sets) { it.slang },
    )
}

/** `1080p`, or `720p–1080p` when a show is not all one thing. */
fun rangeOf(values: List<String>): String? =
    when {
        values.isEmpty() -> null
        values.size == 1 -> values[0]
        else -> "${values.first()}–${values.last()}"
    }

private fun joinedOf(vararg parts: String?): String? =
    parts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }

/** `1080p · HDR10 · h264 · aac`, or `null` when nothing was recorded. */
fun pictureLine(facts: SeriesFacts): String? =
    joinedOf(rangeOf(facts.qualities), *facts.hdr.toTypedArray(), *facts.video.toTypedArray(), *facts.audio.toTypedArray())

/**
 * How much of a show is held: `8 episodes · one season · 5h 58m · 38.7 GB`.
 *
 * When the provider has said how much exists and this library has less, the
 * counts say so — `8 of 16 episodes`. Holding all of it says nothing about
 * totals, because "16 of 16" is a fact about arithmetic, not about the show.
 */
fun scaleLine(
    facts: SeriesFacts,
    info: TitleInfo? = null,
): String? =
    joinedOf(
        held(facts.episodes, info?.totalEpisodes?.toInt(), "episode"),
        facts.seasons.takeIf { it > 0 }?.let { held(it, info?.totalSeasons?.toInt(), "season") },
        humanDuration(facts.runtimeSecs),
        facts.totalBytes.takeIf { it > 0 }?.let(::humanSize),
    )

/**
 * `one season`, or `1 of 2 seasons` when some of it is missing — figures
 * once a comparison is involved, since there the point is the arithmetic.
 */
private fun held(
    have: Int,
    total: Int?,
    noun: String,
): String = if (total == null || total <= have) spelledCountOf(have, noun) else "$have of $total ${noun}s"

/**
 * The years a show ran, or failing that the years this library's copies
 * carry. The provider's dates describe the show; the episodes' years
 * describe what is held — a library with one season of a show that ran
 * seven should say when the show ran, so the dates win where they exist.
 */
fun yearLine(
    facts: SeriesFacts,
    info: TitleInfo? = null,
): String? {
    val first = yearOf(info?.firstAir)
    if (first != null) {
        val last = yearOf(info?.lastAir)
        // A show still running has no last date, and a dash to nowhere would claim it ended.
        return if (last != null && last != first) "$first–$last" else first.toString()
    }
    return facts.years.takeIf { it.isNotEmpty() }?.let { rangeOf(it.map(Int::toString)) }
}

/** The year out of a provider date, which is `YYYY-MM-DD` or absent. */
private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()?.takeIf { it > 1800 }

/**
 * `1987–1994 · two seasons · Sci-Fi, Drama` — the line under a show's
 * title, a port of `series-page.js`'s own `factsLine`: the years it ran,
 * its seasons spelled, and its first three genres.
 */
fun seriesFactsLine(
    facts: SeriesFacts,
    info: TitleInfo?,
    genres: List<String>,
): String =
    listOfNotNull(yearLine(facts, info), spelledCountOf(facts.seasons, "season"), genres.take(3).joinToString(", ").takeIf(String::isNotEmpty))
        .joinToString(" · ")

/**
 * Rating, network and status from the provider, on one line. Genres are
 * their own row in the About tab's fact sheet, so they are not repeated
 * here — mirrors `series-page.js` calling `provenance` with `genres: null`.
 */
fun provenance(info: TitleInfo?): String? = info?.let { joinedOf(ratingLabel(it.rating), it.network, it.status) }

/**
 * The language rows [pictureLine] leaves out, read from the files' own
 * tracks ([model.MediaSet.alang]/[model.MediaSet.slang]) — not the
 * uploader-extracted tracks a viewer can pick in the player. A language
 * nobody recorded yields no row at all, rather than an empty one that
 * would say the library looked and found none.
 */
fun detailRows(facts: SeriesFacts): List<Pair<String, String>> =
    listOf("Audio" to facts.audioLanguages, "Subtitles" to facts.subtitleLanguages)
        .filter { (_, codes) -> codes.isNotEmpty() }
        .map { (label, codes) -> label to codes.joinToString(", ", transform = ::languageName) }

/** A language code as a viewer reads it: `en` → `English`. Mirrors the web's `languageLabel`, minus its own dictionary — `Locale`'s carries the same names. */
fun languageName(code: String): String =
    java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).ifEmpty { code }
