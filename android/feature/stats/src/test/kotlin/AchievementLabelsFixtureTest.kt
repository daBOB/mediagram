package stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own achievement-labels.json against [achievementLabel] and
 * [progressLine]. A case that only passes after changing them does not
 * belong in the fixture — the web is authoritative, and this file exists to
 * agree with it, not redefine it.
 */
class AchievementLabelsFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = webFixture("watch-state/achievement-labels.json")
        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "achievement-labels.json holds no cases")
        for (case in cases) {
            val fields = case.jsonObject
            val id = fields.getValue("id").jsonPrimitive.content
            val have = fields.getValue("have").jsonPrimitive.int.toUInt()
            val need = fields.getValue("need").jsonPrimitive.int.toUInt()
            assertEquals(fields.getValue("label").jsonPrimitive.content, achievementLabel(id), "label: $id")
            assertEquals(fields.getValue("progress").jsonPrimitive.content, progressLine(id, have, need), "progress: $id")
        }
    }
}
