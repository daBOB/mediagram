package catalog

import model.MediaSet
import model.humanSize
import uniffi.mediagram_core.TitleInfo

/**
 * What a show adds up to, from the episodes the catalog already holds —
 * ported from the web's `series-summary.js#summarize`, narrowed to what
 * [TitleInfo] actually carries on Android: no first/last air date and no
 * provider episode/season totals reach this build yet (`TitleInfo` has
 * `overview`/`tagline`/`genres`/`rating`/`network`/`status` and nothing
 * else), so "Aired" and "Held" can only speak from the episodes this
 * library holds, never as a count against a total the provider named.
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
        subtitleLanguages = distinctAll(sets) { it.subtitleLanguages },
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
 * How much of a show is held: `8 episodes · 2 seasons · 5h 58m · 38.7 GB`.
 *
 * Always what this library has, never "8 of 16" — [TitleInfo] carries no
 * provider total to compare against (see this file's own doc comment).
 */
fun scaleLine(facts: SeriesFacts): String? =
    joinedOf(
        held(facts.episodes, "episode"),
        facts.seasons.takeIf { it > 0 }?.let { held(it, "season") },
        humanDuration(facts.runtimeSecs),
        facts.totalBytes.takeIf { it > 0 }?.let(::humanSize),
    )

private fun held(
    count: Int,
    noun: String,
): String = "$count $noun${if (count == 1) "" else "s"}"

/** The years a show ran, from the episodes this library holds — no provider air dates reach this build (see this file's own doc comment). */
fun yearLine(facts: SeriesFacts): String? = facts.years.takeIf { it.isNotEmpty() }?.let { rangeOf(it.map(Int::toString)) }

/**
 * Rating, network and status from the provider, on one line. Genres are
 * their own row in the About tab's fact sheet, so they are not repeated
 * here — mirrors `series-page.js` calling `provenance` with `genres: null`.
 */
fun provenance(info: TitleInfo?): String? = info?.let { joinedOf(ratingLabel(it.rating), it.network, it.status) }

/**
 * The one row [pictureLine] leaves out: subtitle languages. There is no
 * "Audio languages" row to match `series-summary.js`'s own `detailRows` —
 * [model.MediaSet] carries no audio-language field yet, only [model.MediaSet.subtitleLanguages].
 */
fun detailRows(facts: SeriesFacts): List<Pair<String, String>> =
    listOfNotNull(
        facts.subtitleLanguages.takeIf { it.isNotEmpty() }
            ?.let { codes -> "Subtitles" to codes.joinToString(", ", transform = ::languageName) },
    )

/** A language code as a viewer reads it: `en` → `English`. Mirrors the web's `languageLabel`, minus its own dictionary — `Locale`'s carries the same names. */
fun languageName(code: String): String =
    java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).ifEmpty { code }
