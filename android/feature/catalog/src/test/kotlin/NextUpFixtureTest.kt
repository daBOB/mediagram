package catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import model.Kind
import model.MediaSet
import model.Progress
import model.Watched
import model.WatchSnapshot
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own next-up fixtures against [underwayOf] — the Kotlin
 * port of the underway half of `home-shelves.js`. One synthetic show, its
 * episodes numbered by their place in the fixture's `order`, the same shape
 * `shared-watch-state-fixtures.test.ts` builds from the same file. A case
 * that only passes after changing [underwayOf] or [nextInCollection] does
 * not belong here — the web is authoritative.
 */
class NextUpFixtureTest {

    @Test
    fun matchesTheWebsFixtures() {
        val file = locateFixture("next-up.json")
        assumeTrue("next-up.json not found above this module; is the web checkout present?", file != null)

        val cases = Json.parseToJsonElement(file!!.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "next-up.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content

            val order = obj.getValue("order").jsonArray.map { it.jsonPrimitive.content }
            val sets = order.mapIndexed { index, setId -> episodeOf(setId, index + 1) }
            val byId = sets.associateBy(MediaSet::setId)
            val collection = shelvesOf(sets).single { it.title == "Series" }.entries.single() as Entry.Collection

            val progress = obj.getValue("progress").jsonArray.map { it.jsonObject.toProgress() }
            val watched = obj.getValue("watched").jsonObject.entries
                .map { (setId, finishedAt) -> Watched(setId, finishedAt.jsonPrimitive.long) }
            val watch = WatchSnapshot(progress, watched, emptyList(), emptyList(), emptyList())

            val underway = underwayOf(listOf(collection), byId, watch, HOME_ROW_LIMIT)

            val expect = obj.getValue("expect").jsonObject
            val expectedNextUp = expect["nextUp"]?.takeUnless { it is JsonNull }?.jsonObject
            assertEquals(expectedNextUp?.get("setId")?.jsonPrimitive?.content, underway.nextUp.firstOrNull()?.set?.setId, "case: $name (nextUp id)")
            assertEquals(expectedNextUp?.get("resume")?.jsonPrimitive?.boolean, underway.nextUp.firstOrNull()?.resume, "case: $name (nextUp resume)")
            assertEquals(
                expect.getValue("continues").jsonArray.map { it.jsonPrimitive.content },
                underway.continues.map(MediaSet::setId),
                "case: $name (continues)",
            )
            val totals = expect.getValue("totals").jsonObject
            assertEquals(totals.getValue("continues").jsonPrimitive.int, underway.continuesTotal, "case: $name (totals.continues)")
            assertEquals(totals.getValue("nextUp").jsonPrimitive.int, underway.nextUpTotal, "case: $name (totals.nextUp)")
        }
    }
}

/** One episode of a single synthetic show, numbered by its place in the fixture's `order` — mirrors the web test's `episodeOf`. */
private fun episodeOf(setId: String, number: Int): MediaSet = MediaSet(
    setId = setId,
    kind = Kind.EPISODE,
    title = "Show $number",
    show = "Show",
    chapter = null,
    path = null,
    season = 1,
    episodeFirst = number,
    episodeLast = number,
    year = null,
    durationSecs = 3_600,
    posterPath = null,
    totalBytes = 1_000,
    addedAt = 1_000,
)

private fun JsonObject.toProgress(): Progress = Progress(
    setId = getValue("setId").jsonPrimitive.content,
    at = getValue("at").jsonPrimitive.double,
    duration = get("duration")?.jsonPrimitive?.double,
    updatedAt = getValue("updatedAt").jsonPrimitive.long,
)

/** Walks up from the working directory until it finds the web's fixture directory, or gives up at the filesystem root. */
private fun locateFixture(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "web/test/fixtures/watch-state/$name")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}
