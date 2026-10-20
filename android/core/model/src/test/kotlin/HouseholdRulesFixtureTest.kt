package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the web's own `household-rules.json` against [householdAllowed] and the
 * household words. Its `view` and `device` sections are the core's to answer,
 * not this module's. The web decides: a case that only passes after moving
 * the port away from it is a bug here.
 */
class HouseholdRulesFixtureTest {
    private val fixture: JsonObject =
        Json.parseToJsonElement(assertNotNull(locate(), "household-rules.json not found").readText()).jsonObject

    @Test
    fun agreesWithTheWebOnWhoMayDoWhat() {
        val cases = fixture.getValue("allowed").jsonArray
        assertTrue(cases.isNotEmpty(), "household-rules.json holds no allowed cases")
        for (case in cases.map { it.jsonObject }) {
            val wire = case.getValue("action").jsonPrimitive.content
            assertEquals(
                case.getValue("expect").jsonPrimitive.boolean,
                case.getValue("profiles").jsonArray.map { it.jsonObject.toProfile() }.householdAllowed(
                    live = case.getValue("live").jsonArray.map { it.jsonPrimitive.content },
                    actorId = case.getValue("actorId").jsonPrimitive.content,
                    action = HouseholdAction.entries.single { it.wire == wire },
                    householdId = case.getValue("householdId").jsonPrimitive.content,
                    targetId = case["targetId"]?.jsonPrimitive?.contentOrNull,
                ),
                "case: ${case.getValue("name").jsonPrimitive.content}",
            )
        }
    }

    @Test
    fun speaksTheWebsWords() {
        assertEquals(fixture.getValue("words").jsonObject.mapValues { it.value.jsonPrimitive.content }, HOUSEHOLD_WORDS)
        val lines = fixture.getValue("lines").jsonArray
        assertTrue(lines.isNotEmpty(), "household-rules.json holds no lines")
        for (line in lines.map { it.jsonObject }) {
            val args = line.getValue("args").jsonArray
            val said =
                when (val fn = line.getValue("fn").jsonPrimitive.content) {
                    "householdLine" -> householdLine(args[0].jsonPrimitive.contentOrNull, args[1].jsonPrimitive.int)
                    "joinPinTitle" -> joinPinTitle((args[0] as? JsonObject)?.toAdmin())
                    else -> fail("household-rules.json names an unknown line: $fn")
                }
            val name = line.getValue("name").jsonPrimitive.content
            assertEquals(line.getValue("expect").jsonPrimitive.content, said, "line: $name")
        }
    }

    private fun locate(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "web/test/fixtures/watch-state/household-rules.json").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }
}

/** A fixture household admin — `{ name, owner? }` — as the profile [joinPinTitle] reads. */
private fun JsonObject.toAdmin(): Profile {
    val name = getValue("name").jsonPrimitive.content
    return Profile(id = name, name = name, owner = get("owner")?.jsonPrimitive?.boolean ?: false)
}
