package catalog

import java.util.Locale

/*
 * The short facts that sit beside a poster: when a title is from, how long
 * it runs, and what a provider scored it.
 *
 * All pure, none composable — the title detail screen is where these meet a
 * layout, the same division the system screen's rows and the player's
 * technical line already follow. Each answers `null` rather than an empty
 * string when it has nothing to say, so a caller leaves the line out
 * instead of printing a separator with nothing beside it: a blank field
 * reads as a value that failed to load rather than one nobody recorded.
 */

/**
 * A runtime in hours and minutes: a film is `1h 53m`, a lesson `12m`.
 *
 * Mirrors `humanDuration` in the web player's `format.js`, down to rounding
 * to whole minutes and to never printing `0m` — anything short enough to
 * round away is still a minute to a viewer deciding whether to start it.
 */
fun humanDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = Math.round((seconds % 3600) / 60.0).toInt()
    if (hours == 0) return "${maxOf(1, minutes)}m"
    return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
}

/**
 * The line beside a poster: `2004 · 1h 53m · FSK 12`, or whichever parts of
 * it are known, or nothing when none is. The age rating sits after the
 * runtime, where the web's film page puts it; a shelf card leaves it off.
 *
 * A year of zero is what an index writes when it has no year rather than a
 * title from the year zero, so it is not printed.
 */
fun factsLine(
    year: Int?,
    durationSecs: Int?,
    ageLabel: String? = null,
): String? =
    listOfNotNull(
        year?.takeIf { it > 0 }?.toString(),
        humanDuration(durationSecs),
        ageLabel,
    ).joinToString(" · ").takeIf(String::isNotEmpty)

/**
 * A provider's score, as `★ 5.9` — the same star and the same single
 * decimal the web player's `provenance` prints.
 *
 * Formatted against `Locale.ROOT` for the reason every other figure in this
 * module is: a default locale that writes decimal commas would show a
 * rating in a shape the other surface never uses.
 */
fun ratingLabel(rating: Double?): String? = rating?.let { String.format(Locale.ROOT, "★ %.1f", it) }
