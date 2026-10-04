package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the web's own `profile-rules.json` — the file the web's rule and the
 * core's `rules.rs` both answer — against [allowed]. The web decides: a case
 * that only passes after moving [allowed] away from it is a bug here.
 */
class ProfileRolesFixtureTest {
    @Test
    fun agreesWithTheWebOnEveryCase() {
        val file = assertNotNull(locateFixture(), "profile-rules.json not found above ${System.getProperty("user.dir")}")
        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "profile-rules.json holds no cases")
        for (case in cases.map { it.jsonObject }) {
            val wire = case.getValue("action").jsonPrimitive.content
            val action = RoleAction.entries.single { it.wire == wire }
            assertEquals(
                case.getValue("expect").jsonPrimitive.boolean,
                case
                    .getValue("profiles")
                    .jsonArray
                    .map { it.jsonObject.toProfile() }
                    .allowed(case.getValue("actorId").jsonPrimitive.content, action, case["targetId"]?.jsonPrimitive?.contentOrNull),
                "case: ${case.getValue("name").jsonPrimitive.content}",
            )
        }
    }
}

/** A fixture `RoleView` — `{ id, kids, admin, parentId }` — as the profile the rule reads. */
private fun JsonObject.toProfile(): Profile {
    val id = getValue("id").jsonPrimitive.content
    return Profile(
        id = id,
        name = id,
        kids = getValue("kids").jsonPrimitive.boolean,
        admin = getValue("admin").jsonPrimitive.boolean,
        parentId = get("parentId")?.jsonPrimitive?.contentOrNull,
    )
}

private fun locateFixture(): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        File(dir, "web/test/fixtures/watch-state/profile-rules.json").takeIf { it.isFile }?.let { return it }
        dir = dir.parentFile
    }
    return null
}
