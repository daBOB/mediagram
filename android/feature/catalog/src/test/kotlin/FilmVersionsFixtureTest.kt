package catalog

import data.ProgressPoint
import data.ResumePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import model.Kind
import model.MediaSet
import model.Progress
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own film-versions fixtures against `FilmVersions.kt`, the
 * same way `web/test/film-versions.test.ts` runs them against
 * `film-versions.js`: how copies of a film group into one card, in what
 * order, how each is labelled, what is watched and where each copy resumes.
 * A case that only passes after changing the rules here does not belong in
 * the fixture — the web is authoritative.
 */
class FilmVersionsFixtureTest {
    @Test
    fun groupingMatchesTheWebsFixture() {
        val cases = Json.parseToJsonElement(webFixture("film-versions/grouping.json").readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "grouping.json holds no cases")
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val films = groupFilms(obj.getValue("sets").jsonArray.map { it.jsonObject.toSet() })
            val expected =
                obj.getValue("expect").jsonArray.map { film ->
                    val fields = film.jsonObject
                    Triple(
                        fields.getValue("setId").jsonPrimitive.content,
                        fields.getValue("versions").jsonArray.map { it.jsonPrimitive.content },
                        fields.getValue("labels").jsonArray.map { it.jsonPrimitive.content },
                    )
                }
            val actual = films.map { film -> Triple(film.setId, versionsOf(film).map(MediaSet::setId), versionsOf(film).map(::versionLabel)) }
            assertEquals(expected, actual, "case: $name")
        }
    }

    @Test
    fun watchStateMatchesTheWebsFixture() {
        val file = Json.parseToJsonElement(webFixture("film-versions/watch-state.json").readText()).jsonObject
        val copies = file.getValue("film").jsonArray.map { it.jsonObject.toSet().copy(posterKey = "tmdb-movie-1") }
        val film = groupFilms(copies).single()
        val alone = movie(file.getValue("single").jsonPrimitive.content, posterKey = "tmdb-movie-2")
        val index = filmIndexOf(sequenceOf(film, alone))
        val cases = file.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty(), "watch-state.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val rows = obj.getValue("progress").jsonArray.map { it.jsonObject.toProgress() }.associateBy(Progress::setId)
            val watched = obj.getValue("watched").jsonObject.mapValues { it.value.jsonPrimitive.long }
            val newestFirst = rows.values.sortedByDescending(Progress::updatedAt)
            val expect = obj.getValue("expect").jsonObject

            assertEquals(expect.getValue("played").jsonPrimitive.content.toBoolean(), filmPlayed(film, watched::get), "case: $name (played)")
            val resume =
                versionsOf(film).associate { version ->
                    version.setId to resumeOf(versionPosition(version, film, rows::get, watched::get))
                }
            val expectedResume = expect.getValue("resume").jsonObject.mapValues { (_, at) -> at.takeUnless { it is JsonNull }?.jsonPrimitive?.double }
            assertEquals(expectedResume, resume, "case: $name (resume)")
            assertEquals(
                expect.getValue("continue").jsonArray.map { it.jsonPrimitive.content },
                continueRows(newestFirst, index::get, watched::get).filter { resumeOf(it) != null }.map(Progress::setId),
                "case: $name (continue)",
            )
        }
    }

    @Test
    fun aStoredCopyShowsAsItsWholeFilmOnceAndEverythingElseAsItCame() {
        val hd = movie("HD", posterKey = "tmdb-movie-1", quality = "1080p")
        val uhd = movie("UHD", posterKey = "tmdb-movie-1", quality = "2160p")
        val episode = movie("E1").copy(kind = Kind.EPISODE)
        val index = filmIndexOf(groupFilms(listOf(hd, uhd)).asSequence())
        assertEquals(listOf("UHD", "E1"), asFilms(listOf(hd, episode, uhd), index::get).map(MediaSet::setId))
    }
}

private fun resumeOf(row: Progress?): Double? = ResumePoint.resumeAt(row?.let { ProgressPoint(it.at, it.duration) })

/** A fixture row as a set; anything that is not a film stands in as an episode, since what a case asks is that only films group. */
private fun JsonObject.toSet(): MediaSet =
    movie(
        setId = getValue("setId").jsonPrimitive.content,
        posterKey = get("showKey")?.jsonPrimitive?.contentOrNull,
        quality = get("quality")?.jsonPrimitive?.contentOrNull,
    ).copy(
        kind = if ((get("kind")?.jsonPrimitive?.contentOrNull ?: "movie") == "movie") Kind.MOVIE else Kind.EPISODE,
        variant = get("variant")?.jsonPrimitive?.contentOrNull,
        hdr = get("hdr")?.jsonPrimitive?.contentOrNull,
        durationSecs = get("duration")?.jsonPrimitive?.intOrNull ?: 60,
        totalBytes = get("total")?.jsonPrimitive?.longOrNull ?: 1_000,
    )

private fun JsonObject.toProgress(): Progress =
    Progress(
        setId = getValue("setId").jsonPrimitive.content,
        at = getValue("at").jsonPrimitive.double,
        duration = get("duration")?.jsonPrimitive?.doubleOrNull,
        updatedAt = getValue("updatedAt").jsonPrimitive.long,
    )

internal fun movie(
    setId: String,
    posterKey: String? = null,
    quality: String? = null,
): MediaSet =
    MediaSet(
        setId = setId,
        kind = Kind.MOVIE,
        title = "A Title",
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = 60,
        posterPath = null,
        totalBytes = 1_000,
        container = "mp4",
        vcodec = "h264",
        acodec = "aac",
        quality = quality,
        posterKey = posterKey,
    )
