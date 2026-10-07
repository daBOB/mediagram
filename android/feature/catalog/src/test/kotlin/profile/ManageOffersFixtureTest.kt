package catalog.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.Profile
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds Manage profiles' hand-coded panel ([manageable]) to the web's
 * `profile-rules.json`, the way the web holds its own panel: whatever the
 * panel offers, the core's rule must allow, and the reverse. The derivation
 * of "offered" is the web's `offered()` in `profile-api.test.ts`, line for
 * line — the web decides; a case that only passes after moving [manageable]
 * away from it is a bug here.
 */
class ManageOffersFixtureTest {
    @Test
    fun offersWhatTheRuleAllowsOnEveryCase() {
        val cases = Json.parseToJsonElement(webFixture("watch-state/profile-rules.json").readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "profile-rules.json holds no cases")
        for (case in cases.map { it.jsonObject }) {
            val profiles = case.getValue("profiles").jsonArray.map { it.jsonObject.toProfile() }
            assertEquals(
                case.getValue("expect").jsonPrimitive.boolean,
                offered(
                    profiles,
                    case.getValue("actorId").jsonPrimitive.content,
                    case.getValue("action").jsonPrimitive.content,
                    case["targetId"]?.jsonPrimitive?.contentOrNull,
                ),
                "case: ${case.getValue("name").jsonPrimitive.content}",
            )
        }
    }
}

/** Whether the panel offers [action] on [targetId]: a button shown, or a row listed. */
private fun offered(
    profiles: List<Profile>,
    actorId: String,
    action: String,
    targetId: String?,
): Boolean {
    val panel = manageable(profiles, actorId, null) as? ManageUiState.Managing ?: return false
    fun List<Profile>.has() = any { it.id == targetId }
    return when (action) {
        "create-grown-up" -> panel.canAddGrownUp
        "create-kid" -> true
        "remove" -> panel.grownUps.has() || panel.kids.has()
        "set-pin" -> targetId == actorId || panel.grownUps.has()
        "set-kids-age" -> panel.kids.has()
        else -> error("Unknown action $action")
    }
}

/** A fixture `RoleView` — `{ id, kids, admin, parentId }` — as the profile the panel reads. */
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
