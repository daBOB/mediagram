package player

import model.Kind
import model.MediaSet

/**
 * A [MediaSet] with sensible defaults, so a test only names what it cares
 * about. [rawTitle] mirrors [title] by default — the ordinary case, where
 * the index actually named the set what [title] says — so a test naming a
 * real title need not repeat it; a test of the untitled case overrides
 * [rawTitle] to `null` explicitly rather than relying on any default
 * derived from [setId].
 */
fun fakeMediaSet(
    setId: String = "s1",
    kind: Kind = Kind.MOVIE,
    title: String = setId,
    rawTitle: String? = title,
    show: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    episodeLast: Int? = null,
    posterKey: String? = null,
    posterPath: String? = null,
    durationSecs: Int? = null,
    fsk: String? = null,
): MediaSet = MediaSet(
    setId = setId,
    kind = kind,
    title = title,
    rawTitle = rawTitle,
    show = show,
    chapter = null,
    path = null,
    season = season,
    episodeFirst = episodeFirst,
    episodeLast = episodeLast,
    year = null,
    durationSecs = durationSecs,
    posterPath = posterPath,
    totalBytes = 0,
    posterKey = posterKey,
    fsk = fsk,
)
