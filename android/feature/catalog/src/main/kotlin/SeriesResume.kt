package catalog

import model.Kind
import model.MediaSet

/** In order of how plausibly the viewer wants it, what a "Resume" button offers. */
enum class ResumeVerb { RESUME, CONTINUE, PLAY }

/** What a show's page offers to carry on with. */
data class SeriesResumePick(val set: MediaSet, val at: Double?, val verb: ResumeVerb)

/**
 * Where a show's page offers to carry on — ported from `seriesResume` in the
 * web's `series-resume.js`. Flattening is [playOrder]'s, already used by
 * [nextInCollection]; this only orders the three cases:
 *
 *   1. the episode of this show the viewer last stopped partway through;
 *   2. otherwise the one after the furthest episode they finished;
 *   3. otherwise the first episode.
 *
 * A show watched to its end offers its first episode again.
 *
 * @param resumeOf a set's saved position, already judged by [data.ResumePoint]
 * @param recent every set id with a position, most recently touched first
 * @param watched whether the viewer finished a set
 */
fun seriesResume(
    divisions: List<Division>,
    resumeOf: (String) -> Double?,
    recent: List<String>,
    watched: (String) -> Boolean,
): SeriesResumePick? {
    val episodes = playOrder(divisions)
    if (episodes.isEmpty()) return null

    val inShow = episodes.mapTo(HashSet(), MediaSet::setId)
    for (setId in recent) {
        if (setId !in inShow) continue
        val at = resumeOf(setId) ?: continue
        val set = episodes.first { it.setId == setId }
        return SeriesResumePick(set, at, ResumeVerb.RESUME)
    }

    var furthest = -1
    episodes.forEachIndexed { index, set -> if (watched(set.setId)) furthest = index }
    if (furthest >= 0) {
        episodes.getOrNull(furthest + 1)?.let { return SeriesResumePick(it, null, ResumeVerb.CONTINUE) }
    }
    return SeriesResumePick(episodes[0], null, ResumeVerb.PLAY)
}

/** The word a resume pill starts with — the `verb` `seriesResume` answers in series-resume.js. */
val ResumeVerb.word: String
    get() =
        when (this) {
            ResumeVerb.RESUME -> "Resume"
            ResumeVerb.CONTINUE -> "Continue"
            ResumeVerb.PLAY -> "Play"
        }

/**
 * What a resume pill says after its mark — `Resume S1 E3`, the verb and
 * [episodeShort], as `series-page.js` words a show's. A course's lesson
 * says `Continue lesson 4` instead: the season a lesson carries is the
 * chapter it was filed under, so `S1 E4` would name a season the course
 * does not have. Anything else in a course (a documentary) goes by its title.
 */
fun resumeWordsOf(pick: SeriesResumePick): String {
    val set = pick.set
    val target =
        when {
            set.kind == Kind.EPISODE -> episodeShort(set)
            set.kind == Kind.TUTORIAL && set.episodeFirst != null -> "lesson ${set.episodeFirst}"
            else -> set.title
        }
    return "${pick.verb.word} $target"
}

/** `S3 E15` for an episode with both numbers, else its title — ported from `episodeShort` in series-resume.js. */
fun episodeShort(set: MediaSet): String {
    val episode = set.episodeFirst
    return if (set.season != null && episode != null) "S${set.season} E$episode" else set.title
}
