package data

import model.Kind
import model.MediaSet
import model.SubtitleTrackInfo
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.SubtitleTrack

/**
 * One index row, as a set the shelves can place.
 *
 * A kind this does not recognise is shelved with the films rather than
 * dropped. A viewer who notices something in the wrong place can act on
 * it; a title that silently vanishes looks like a failed upload, and the
 * library holds four kinds today against a parser that may learn a
 * fifth before this app is rebuilt.
 */
fun SetSummary.toMediaSet(): MediaSet {
    val shelf =
        when (kind) {
            "ep" -> Kind.EPISODE
            "tut" -> Kind.TUTORIAL
            "doc" -> Kind.DOCUMENT
            "docu" -> Kind.DOCUMENTARY
            else -> Kind.MOVIE
        }
    return MediaSet(
        setId = setId,
        kind = shelf,
        title = title ?: show ?: setId,
        rawTitle = title,
        show = show,
        chapter = chap,
        path = path,
        season = season?.toInt(),
        episodeFirst = episodeFirst?.toInt(),
        episodeLast = episodeLast?.toInt(),
        year = year?.toInt(),
        durationSecs = duration?.toInt(),
        posterPath = posterPath,
        totalBytes = total.toLong(),
        container = container,
        vcodec = vcodec,
        acodec = acodec,
        quality = quality,
        hdr = hdr,
        partCount = partCount.toInt(),
        posterKey = posterKey,
        addedAt = addedAt,
        fsk = fsk,
        genres = genres,
        subtitles = subtitles.map { it.toSubtitleTrackInfo() },
        alang = alang,
        slang = slang,
        hasSummary = hasSummary,
        backdropPath = backdropPath,
        seasonPosterPath = seasonPosterPath,
        tagline = tagline,
        rating = rating,
        popularity = popularity,
        collectionId = collectionId?.toLong(),
        collectionName = collectionName,
        seriesType = seriesType,
        showStatus = showStatus,
        anime = anime,
        category = category,
    )
}

private fun SubtitleTrack.toSubtitleTrackInfo(): SubtitleTrackInfo = SubtitleTrackInfo(track.toInt(), lang, forced, sdh, label)
