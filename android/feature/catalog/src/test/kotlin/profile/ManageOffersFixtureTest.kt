package catalog.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.HOME_HOUSEHOLD
import model.Household
import model.Households
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
 * away from it is a bug here. Adding is the exception: the web's panel draws
 * its add forms on the page, so its `offered()` asks the rule, while here
 * each part ([parts]) says what it adds and for whom, so those are checked.
 */
class ManageOffersFixtureTest {
    @Test
    fun offersWhatTheRuleAllowsOnEveryCase() {
        val cases = Json.parseToJsonElement(webFixture("watch-state/profile-rules.json").readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "profile-rules.json holds no cases")
        // Every case that disagrees, by name, so one run lists them all.
        val wrong =
            cases.map { it.jsonObject }.filter { case ->
                val offers =
                    offered(
                        case.getValue("profiles").jsonArray.map { it.jsonObject.toProfile() },
                        case.getValue("actorId").jsonPrimitive.content,
                        case.getValue("action").jsonPrimitive.content,
                        case["targetId"]?.jsonPrimitive?.contentOrNull,
                    )
                offers != case.getValue("expect").jsonPrimitive.boolean
            }
        assertEquals(emptyList(), wrong.map { it.getValue("name").jsonPrimitive.content })
    }
}

/** Whether the panel offers [action] on [targetId]: a button shown, or a row listed. */
private fun offered(
    profiles: List<Profile>,
    actorId: String,
    action: String,
    targetId: String?,
): Boolean {
    // The panel is given the actor's household as this device's, and every household on the install, as the households are read.
    val mine = profiles.find { it.id == actorId }?.household ?: HOME_HOUSEHOLD
    val listed = profiles.map { it.household }.distinct().map { id -> Household(id, id, profiles.find { it.household == id && it.admin && !it.kids }?.id) }
    val install = Households(mine, listed, profiles)
    val panel = manageable(profiles.filter { it.household == mine }, actorId, null, install = install) as? ManageUiState.Managing ?: return false
    val parts = panel.parts()
    fun List<Profile>.has(id: String? = targetId) = any { it.id == id }
    // A new profile goes where it is named — a household, or the part listing its parent — or, named nowhere, to the maker's own part.
    val named = targetId?.takeIf { it.isNotEmpty() }
    return when (action) {
        "create-grown-up" -> (named ?: mine).let { into -> parts.any { it.addsGrownUp && (it.id == into || (it.id == null && into == mine)) } }
        "create-kid" -> (named ?: actorId).let { parent -> parts.any { part -> part.addsKid && (part.kidParents.has(parent) || (part.id == null && parent == actorId)) } }
        "remove" -> panel.grownUps.has() || panel.kids.has()
        "set-pin" -> targetId == actorId || panel.grownUps.has()
        "set-kids-age" -> panel.kids.has()
        "set-access" -> panel.grownUps.has() || panel.kids.has()
        else -> error("Unknown action $action")
    }
}

/** A fixture `RoleView` — `{ id, kids, admin, owner?, parentId, household? }` — as the profile the panel reads. */
private fun JsonObject.toProfile(): Profile {
    val id = getValue("id").jsonPrimitive.content
    return Profile(
        id = id,
        name = id,
        kids = getValue("kids").jsonPrimitive.boolean,
        admin = getValue("admin").jsonPrimitive.boolean,
        parentId = get("parentId")?.jsonPrimitive?.contentOrNull,
        household = get("household")?.jsonPrimitive?.contentOrNull ?: HOME_HOUSEHOLD,
        owner = get("owner")?.jsonPrimitive?.boolean ?: false,
    )
}
