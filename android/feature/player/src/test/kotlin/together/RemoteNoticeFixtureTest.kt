package player.together

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.together.Change
import model.together.Noticed
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The notices of a cast are the web's own: `cast.json` holds them for `sayOnRemote` in `cast-words.js`. */
class RemoteNoticeFixtureTest {
    @Test
    fun remoteNoticesAgreeWithTheWeb() {
        val root = Json.parseToJsonElement(webFixture("together/cast.json").readText()).jsonObject
        val cases = root.getValue("remoteNotices").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val noticed = case.getValue("noticed").jsonObject
            val session = case.getValue("session").jsonObject
            val change = Change.valueOf(noticed.getValue("change").jsonPrimitive.content.uppercase())
            val label = noticed["label"]?.jsonPrimitive?.content
            val expected = case.getValue("expect").jsonPrimitive.let { if (it.isString) it.content else null }
            val shown = TV_SESSION.copy(pos = session.getValue("pos").jsonPrimitive.content.toDouble(), rate = session.getValue("rate").jsonPrimitive.content.toDouble())
            assertEquals(expected, sayOnRemote(Noticed(change, label), shown), case.getValue("name").jsonPrimitive.content)
        }
    }
}
