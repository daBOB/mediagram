package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Holds [dailyLimitState], the words and the menu to the web's own `daily-limit.json`. */
class DailyLimitFixtureTest {
    private val fixture: JsonObject =
        Json.parseToJsonElement(assertNotNull(locate(), "daily-limit.json not found").readText()).jsonObject

    @Test
    fun agreesWithTheWebOnEveryCase() {
        val cases = fixture.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty(), "daily-limit.json holds no cases")
        for (case in cases.map { it.jsonObject }) {
            val expect = case.getValue("expect").jsonObject
            val got =
                dailyLimitState(
                    case.getValue("limitMinutes").jsonPrimitive.intOrNull,
                    case.getValue("watchedSeconds").jsonPrimitive.double,
                )
            val name = case.getValue("name").jsonPrimitive.content
            assertEquals(expect.getValue("state").jsonPrimitive.content, got.state.name.lowercase(), name)
            assertEquals(expect.getValue("leftSeconds").jsonPrimitive.doubleOrNull, got.leftSeconds, name)
        }
    }

    @Test
    fun wordsMenuAndWarningThresholdAreTheFixtures() {
        val words = fixture.getValue("words").jsonObject
        assertEquals(words.getValue("label").jsonPrimitive.content, DailyLimitWords.LABEL)
        assertEquals(words.getValue("warning").jsonPrimitive.content, DailyLimitWords.WARNING)
        assertEquals(words.getValue("out").jsonPrimitive.content, DailyLimitWords.OUT)
        assertEquals(fixture.getValue("warnSeconds").jsonPrimitive.double, DAILY_WARN_SECONDS)
        val choices =
            fixture.getValue("choices").jsonArray.map {
                val c = it.jsonObject
                DailyLimitChoice(c.getValue("minutes").takeIf { m -> m !is JsonNull }?.jsonPrimitive?.intOrNull, c.getValue("label").jsonPrimitive.content)
            }
        assertEquals(choices, DAILY_LIMIT_CHOICES)
    }

    @Test
    fun aLimitOffTheMenuShowsAsItself() {
        assertEquals("50 min", dailyLimitChoiceFor(50).label)
        assertEquals("1 h", dailyLimitChoiceFor(60).label)
        assertEquals("No limit", dailyLimitChoiceFor(null).label)
    }

    private fun locate(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "web/test/fixtures/watch-state/daily-limit.json").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }
}
