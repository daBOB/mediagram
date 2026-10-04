package catalog

import model.MediaSet
import model.ageLabel
import model.bitrateLabel
import model.hdrLabel
import model.humanSize
import uniffi.mediagram_core.TitleInfo

/**
 * What one row of a title page's fact sheet says — the value half of
 * `title-spread.js`'s `factSheet` rows. Words most of the time; a row whose
 * value leads somewhere says where, and each surface draws that as its own
 * kind of link (a tap on the phone, a stop the remote rests on on the
 * television). One builder for both surfaces, so the two cannot list a
 * title's facts in a different order or under a different label.
 */
sealed interface FactValue {
    data class Words(val text: String) : FactValue

    /** The catalogue's own genres, each opening its page. */
    data class Genres(val names: List<String>) : FactValue

    /** The franchise a film belongs to, opening its page. */
    data class PartOf(val franchise: Franchise) : FactValue
}

/** A labelled fact sheet row. */
typealias FactRow = Pair<String, FactValue>

/** A words row, or none when there is nothing to say — the web leaves an empty row out rather than printing it blank. */
private fun words(
    label: String,
    value: String?,
): FactRow? = value?.takeIf(String::isNotEmpty)?.let { label to FactValue.Words(it) }

/**
 * A film's Overview tab, beside its poster — `film-page.js#overview`:
 * released, runtime, rating, score, genres, and the franchise it is part of
 * when the library holds one.
 */
fun filmOverviewFacts(
    set: MediaSet,
    info: TitleInfo?,
    franchise: Franchise?,
): List<FactRow> =
    listOfNotNull(
        words("Released", set.year?.takeIf { it > 0 }?.toString()),
        words("Runtime", humanDuration(set.durationSecs)),
        words("Rated", set.ageLabel()),
        words("Score", ratingLabel(info?.rating)),
        set.genres.takeIf { it.isNotEmpty() }?.let { "Genres" to FactValue.Genres(it) },
        franchise?.let { "Part of" to FactValue.PartOf(it) },
    )

/**
 * A show's About tab — `series-page.js#fillAbout`: when it aired, how much
 * of it is held, who made it, its genres, the picture, then the languages.
 *
 * [genres] are the catalogue's own (the first episode's), the field a genre
 * page is matched against, so each one opens a page that has the show on it.
 */
fun seriesAboutFacts(
    facts: SeriesFacts,
    info: TitleInfo?,
    genres: List<String>,
): List<FactRow> =
    listOfNotNull(
        words("Aired", yearLine(facts, info)),
        words("Held", scaleLine(facts, info)),
        words("From", provenance(info)),
        genres.takeIf { it.isNotEmpty() }?.let { "Genres" to FactValue.Genres(it) },
        words("Picture", pictureLine(facts)),
    ) + detailRows(facts).map { (label, value) -> label to FactValue.Words(value) }

/** What the file is: the questions a viewer asks when a title will not play — `film-page.js#details`. */
fun filmDetailFacts(set: MediaSet): List<FactRow> =
    listOfNotNull(
        words("Quality", listOfNotNull(set.quality, hdrLabel(set.hdr)).joinToString(" · ")),
        words("Video", set.vcodec),
        words("Audio", set.acodec),
        words("Audio languages", languagesOf(set.alang)),
        words("Subtitles", languagesOf(set.slang)),
        words("Container", set.container),
        words("Size", set.totalBytes.takeIf { it > 0 }?.let(::humanSize)),
        words("Bitrate", bitrateLabel(set.totalBytes, set.durationSecs)),
        words("Parts", set.partCount.takeIf { it > 1 }?.toString()),
    )

/** `English, German` from the file's own track codes, or no row when it recorded none. */
private fun languagesOf(codes: List<String>): String? = codes.takeIf { it.isNotEmpty() }?.joinToString(", ", transform = ::languageName)
