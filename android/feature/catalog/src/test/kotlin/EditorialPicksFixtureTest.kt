package catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import model.MediaSet
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own editorial-picks fixtures against [homeEditorial] — the
 * seeded cover, features and quote a redraw must reproduce exactly, day for
 * day, so the two surfaces never disagree about what the magazine home page
 * leads with. A case that only passes after changing [homeEditorial],
 * [seededRandom] or [pickFeatured] does not belong here — the web is
 * authoritative; see `web/test/editorial-picks.test.ts`'s own run of the
 * same file, which is what proves it is not drifting from `homeEditorial.js`.
 */
class EditorialPicksFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = locateFixture("home-editorial.json")
        assumeTrue("home-editorial.json not found above this module; is the web checkout present?", file != null)

        val cases = Json.parseToJsonElement(file!!.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "home-editorial.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content

            val movies = obj.getValue("movies").jsonArray.map { it.jsonObject.toFilm() }
            val byId = movies.associateBy(MediaSet::setId)
            val watchedIds = obj["watchedIds"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
            val onRow = obj.getValue("onRow").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val editorsChoice = obj["editorsChoice"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            val now = obj.getValue("now").jsonPrimitive.long

            val picks =
                homeEditorial(
                    movies = movies,
                    byId = byId,
                    isWatched = { it in watchedIds },
                    editorsChoice = editorsChoice,
                    now = now,
                    onRow = onRow,
                )

            val expect = obj.getValue("expect").jsonObject
            assertEquals(
                expect.getValue("cover").jsonArray.map { it.jsonPrimitive.content },
                picks.cover.map(MediaSet::setId),
                "case: $name (cover)",
            )
            assertEquals(
                expect.getValue("features").jsonArray.map {
                    val f = it.jsonObject
                    f.getValue("kind").jsonPrimitive.content to f.getValue("setId").jsonPrimitive.content
                },
                picks.features.map { it.kind.name.lowercase() to it.set.setId },
                "case: $name (features)",
            )
            assertEquals(
                expect.getValue("quote").takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                picks.quote?.setId,
                "case: $name (quote)",
            )
            assertEquals(
                expect.getValue("thisMonth").jsonArray.map { it.jsonPrimitive.content },
                picks.thisMonth.map(MediaSet::setId),
                "case: $name (thisMonth)",
            )
        }
    }
}

/** One fixture film, carrying only the facts `homeEditorial` reads. */
private fun JsonObject.toFilm(): MediaSet {
    val setId = getValue("setId").jsonPrimitive.content
    return MediaSet(
        setId = setId,
        kind = model.Kind.MOVIE,
        title = getValue("title").jsonPrimitive.content,
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = get("poster")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
        totalBytes = 0,
        addedAt = getValue("addedAt").jsonPrimitive.long,
        backdropPath = get("backdrop")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
        tagline = get("tagline")?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
        rating = get("rating")?.takeUnless { it is JsonNull }?.jsonPrimitive?.double,
        popularity = get("popularity")?.takeUnless { it is JsonNull }?.jsonPrimitive?.double,
    )
}

/** Walks up from the working directory until it finds the web's fixture directory, or gives up at the filesystem root. */
private fun locateFixture(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "web/test/fixtures/editorial-picks/$name")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}
