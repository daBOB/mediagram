package catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.Watched
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's library-pages fixture against [shelvesOf] and
 * [libraryPagesOf] — the departments, Home's resume strip and Latest rows,
 * the editorial picks and each department's own resume strip, for one
 * library. `web/test/library-pages-fixtures.test.ts` runs the same file
 * against `groupDepartments`, `homeShelves`, `homeEditorial` and
 * `departmentUnderway`, which is what proves it describes the web; a case
 * that only passes after changing the projection does not belong here.
 */
class LibraryPagesFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val cases = Json.parseToJsonElement(webFixture("library-pages/cases.json").readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "cases.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val sets = obj.getValue("sets").jsonArray.map { it.jsonObject.toMediaSet() }
            val watch =
                WatchSnapshot(
                    progress = obj.getValue("progress").jsonArray.map { it.jsonObject.toProgress() },
                    watched = obj.getValue("watched").jsonObject.map { (setId, at) -> Watched(setId, at.jsonPrimitive.long) },
                    watchlist = emptyList(),
                    kids = emptyList(),
                    collections = emptyList(),
                    editorsChoice = obj["editorsChoice"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                )
            val shelves = shelvesOf(sets)
            val pages = libraryPagesOf(shelves, watch, heldIds = emptySet(), now = obj.getValue("now").jsonPrimitive.long)
            val expect = obj.getValue("expect").jsonObject

            val departments = expect.getValue("departments").jsonObject
            fun entriesOf(department: Department) = shelves.single { it.department == department }.entries
            fun namesOf(department: Department) = entriesOf(department).filterIsInstance<Entry.Collection>().map { it.name }
            fun filmIdsOf(department: Department) = entriesOf(department).filterIsInstance<Entry.Film>().map { it.set.setId }
            assertEquals(departments.strings("movies"), filmIdsOf(Department.MOVIES), "case: $name (movies)")
            assertEquals(departments.strings("series"), namesOf(Department.SERIES), "case: $name (series)")
            val anime = departments.getValue("anime").jsonObject
            assertEquals(anime.strings("shows") to anime.strings("films"), namesOf(Department.ANIME) to filmIdsOf(Department.ANIME), "case: $name (anime)")
            val documentaries = departments.getValue("documentaries").jsonObject
            assertEquals(
                documentaries.strings("collections") to documentaries.strings("singles"),
                namesOf(Department.DOCUMENTARIES) to filmIdsOf(Department.DOCUMENTARIES),
                "case: $name (documentaries)",
            )
            assertEquals(departments.strings("tutorials"), namesOf(Department.TUTORIALS), "case: $name (tutorials)")

            val home = expect.getValue("home").jsonObject
            val totals = home.getValue("totals").jsonObject
            val magazine = pages.home.magazine
            assertStrip(home, magazine.resumeCards, "case: $name (home)")
            assertEquals(home.strings("latestMovies"), magazine.recentlyAdded.map(MediaSet::setId), "case: $name (latest films)")
            assertEquals(home.strings("latestSeries"), pages.home.latest.series.map { it.name }, "case: $name (latest series)")
            assertEquals(home.strings("latestCourses"), pages.home.latest.courses.map { it.name }, "case: $name (latest courses)")
            assertEquals(
                listOf("continues", "latestMovies", "latestSeries", "latestCourses").map { totals.getValue(it).jsonPrimitive.int },
                listOf(pages.continueWall.size, magazine.recentlyAddedTotal, pages.home.latest.seriesTotal, pages.home.latest.coursesTotal),
                "case: $name (totals)",
            )

            val editorial = expect.getValue("editorial").jsonObject
            val picks = magazine.editorial
            assertEquals(editorial.strings("cover"), picks.cover.map(MediaSet::setId), "case: $name (cover)")
            assertEquals(
                editorial.getValue("features").jsonArray.map { it.jsonObject.let { f -> f.string("kind") to f.string("setId") } },
                picks.features.map { it.kind.name.lowercase() to it.set.setId },
                "case: $name (features)",
            )
            assertEquals(editorial["quote"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content, picks.quote?.setId, "case: $name (quote)")
            assertEquals(editorial.strings("thisMonth"), picks.thisMonth.map(MediaSet::setId), "case: $name (this month)")

            val underway = expect.getValue("underway").jsonObject
            assertStrip(underway.getValue("series").jsonObject, pages.series?.resumeCards.orEmpty(), "case: $name (series strip)")
            assertStrip(underway.getValue("anime").jsonObject, pages.anime?.resumeCards.orEmpty(), "case: $name (anime strip)")
            assertStrip(underway.getValue("documentaries").jsonObject, pages.documentaries?.resumeCards.orEmpty(), "case: $name (documentaries strip)")
            assertStrip(underway.getValue("tutorials").jsonObject, pages.tutorials?.resumeCards.orEmpty(), "case: $name (tutorials strip)")
        }
    }

    /** A resume strip is the web's `continues` then its `nextUp`, an entry that is not a resume captioned "Next up". */
    private fun assertStrip(
        expect: JsonObject,
        cards: List<SetCard>,
        message: String,
    ) {
        val nextUp = expect.getValue("nextUp").jsonArray.map { it.jsonObject }
        assertEquals(expect.strings("continues") + nextUp.map { it.string("setId") }, cards.map { it.set.setId }, message)
        assertEquals(
            nextUp.map { !it.getValue("resume").jsonPrimitive.boolean },
            cards.takeLast(nextUp.size).map { it.caption == "Next up" },
            "$message (next up captions)",
        )
    }
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.strings(key: String): List<String> = getValue(key).jsonArray.map { it.jsonPrimitive.content }

private fun JsonObject.optional(key: String): JsonElement? = get(key)?.takeUnless { it is JsonNull }

/** One fixture set, in the web's own kinds ("movie", "ep", "tut", "docu"). */
private fun JsonObject.toMediaSet(): MediaSet =
    MediaSet(
        setId = string("setId"),
        kind =
            when (val kind = string("kind")) {
                "movie" -> Kind.MOVIE
                "ep" -> Kind.EPISODE
                "tut" -> Kind.TUTORIAL
                "docu" -> Kind.DOCUMENTARY
                else -> error("unknown kind $kind")
            },
        title = string("title"),
        show = optional("show")?.jsonPrimitive?.content,
        chapter = null,
        path = null,
        season = optional("season")?.jsonPrimitive?.intOrNull,
        episodeFirst = optional("episode")?.jsonPrimitive?.intOrNull,
        episodeLast = optional("episode")?.jsonPrimitive?.intOrNull,
        year = null,
        durationSecs = null,
        posterPath = optional("poster")?.jsonPrimitive?.content,
        totalBytes = 0,
        addedAt = getValue("addedAt").jsonPrimitive.long,
        anime = optional("anime")?.jsonPrimitive?.booleanOrNull ?: false,
        backdropPath = optional("backdrop")?.jsonPrimitive?.content,
        rating = optional("rating")?.jsonPrimitive?.doubleOrNull,
        popularity = optional("popularity")?.jsonPrimitive?.doubleOrNull,
        tagline = optional("tagline")?.jsonPrimitive?.content,
    )

private fun JsonObject.toProgress(): Progress =
    Progress(
        setId = string("setId"),
        at = getValue("at").jsonPrimitive.double,
        duration = getValue("duration").jsonPrimitive.double,
        updatedAt = getValue("updatedAt").jsonPrimitive.long,
    )
