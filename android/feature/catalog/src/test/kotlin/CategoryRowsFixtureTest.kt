package catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own `rows.json` cases against [categoryRowsOf] — the row
 * rule a category strip on either department page must reproduce exactly. A
 * case that only passes after changing [categoryRowsOf] does not belong
 * here; the web is authoritative, the same rule [EditorialPicksFixtureTest]
 * follows for its own fixture.
 */
class CategoryRowsFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = webFixture("categories/rows.json")

        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "rows.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val units = obj.getValue("units").jsonArray.map { it.jsonObject }
            val byName = units.associateBy { it.getValue("name").jsonPrimitive.content }

            val rows = categoryRowsOf(units.map { it.getValue("name").jsonPrimitive.content }) { unitName ->
                byName.getValue(unitName)["category"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            }

            val expected = obj.getValue("rows").jsonArray.map { row ->
                val r = row.jsonObject
                r.getValue("title").jsonPrimitive.content to r.getValue("units").jsonArray.map { it.jsonPrimitive.content }
            }
            assertEquals(expected, rows.map { it.title to it.units }, "case: $name")
        }
    }
}
