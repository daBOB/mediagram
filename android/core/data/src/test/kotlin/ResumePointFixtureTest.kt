package data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import testing.webFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own resume-point fixtures against this port of
 * `resume-point.js`. A case that only passes after changing [ResumePoint]
 * does not belong in the fixture — the web is authoritative, and this file
 * exists to agree with it, not redefine it.
 */
class ResumePointFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = webFixture("watch-state/resume-point.json")

        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "resume-point.json holds no cases")

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val args = obj.getValue("args").jsonArray
            when (val fn = obj.getValue("fn").jsonPrimitive.content) {
                "resumeAt" -> {
                    assertEquals(
                        obj.expectDoubleOrNull(),
                        ResumePoint.resumeAt(args[0].toProgressPoint()),
                        "case: $name",
                    )
                }

                "isFinished" -> {
                    assertEquals(
                        obj.getValue("expect").jsonPrimitive.boolean,
                        ResumePoint.isFinished(args[0].asJsNumber(), args[1].asJsNumber()),
                        "case: $name",
                    )
                }

                "watchedFraction" -> {
                    assertEquals(
                        obj.expectDoubleOrNull(),
                        ResumePoint.watchedFraction(args[0].toProgressPoint()),
                        "case: $name",
                    )
                }

                "trustedRuntime" -> {
                    val row = args[0].jsonObject
                    assertEquals(
                        obj.getValue("expect").jsonPrimitive.double,
                        ResumePoint.trustedRuntime(
                            catalogued = row["catalogued"].asNullableJsNumber(),
                            observed = row["observed"].asNullableJsNumber(),
                            direct = row["direct"]?.jsonPrimitive?.boolean ?: false,
                        ),
                        "case: $name",
                    )
                }

                else -> {
                    error("unknown fixture function: $fn")
                }
            }
        }
    }
}

private fun JsonObject.expectDoubleOrNull(): Double? = getValue("expect").let { if (it is JsonNull) null else it.jsonPrimitive.double }

/** `progress` arguments are either `null` or `{at, duration}`; the fixture never omits either key. */
private fun JsonElement.toProgressPoint(): ProgressPoint? {
    if (this is JsonNull) return null
    val obj = jsonObject
    return ProgressPoint(at = obj.getValue("at").asJsNumber(), duration = obj["duration"].asNullableJsNumber())
}

/** Mirrors `Number(x)`: a numeric string parses, anything else unreadable is `NaN`. */
private fun JsonElement.asJsNumber(): Double {
    val primitive = this as? JsonPrimitive ?: return Double.NaN
    return if (primitive.isString) primitive.content.toDoubleOrNull() ?: Double.NaN else primitive.double
}

/** `null` and an absent key both mean "nothing recorded", not zero. */
private fun JsonElement?.asNullableJsNumber(): Double? =
    when (this) {
        null, is JsonNull -> null
        else -> asJsNumber()
    }
