package model.together

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Runs the web's own `web/test/fixtures/together` files against the Kotlin port. The
 * web decides: a case that passes only after moving the port away from it is
 * a bug in the port.
 */
class TogetherFixtureTest {
    @Test
    fun formatAgreesWithTheWeb() {
        for (case in cases("format.json")) {
            val parsed = parseSession(case.str("text")!!)
            val expected = case["expect"]!!.let { if (it is JsonNull) null else it.jsonObject.toSession() }
            assertEquals(expected, parsed, case.label())
            if (parsed != null) assertEquals(parsed, parseSession(writeSession(parsed)), "${case.label()} (round trip)")
        }
    }

    @Test
    fun writeSessionIsByteIdenticalToJsonStringify() {
        val s = Session("s1", "film-1", true, 0.0, 1.0, 1791500000000, "tv-1", listOf(Member("tv-1", "TV")), false)
        val expected =
            "#mlib-together v=1\n" +
                """{"session":"s1","set":"film-1","playing":true,"pos":0,"rate":1,"at":1791500000000,"by":"tv-1",""" +
                """"members":[{"screen":"tv-1","label":"TV"}],"ended":false}"""
        assertEquals(expected, writeSession(s))
        assertTrue(writeSession(s.copy(pos = 12.5, rate = 1.5)).contains(""""pos":12.5,"rate":1.5"""))
    }

    @Test
    fun movesAgreeWithTheWeb() {
        for (case in cases("moves.json")) {
            val args = case.getValue("args").jsonObject
            val session = case["session"]?.jsonObject?.toSession()
            val got =
                when (val move = case.str("move")) {
                    "started" -> started(args.str("id")!!, args.member("me"), args.getValue("own").jsonObject.toPlayback(), args.long("now"))
                    "joined" -> joined(session!!, args.member("me"))
                    "left" -> left(session!!, args.str("screen")!!)
                    "finished" -> finished(session!!, args.str("screen")!!)
                    "acted" -> acted(session!!, args.getValue("change").jsonObject.toChange(), args.str("me")!!, args.long("now"))
                    else -> error("unknown move $move")
                }
            assertEquals(case.getValue("expect").jsonObject.toSession(), got, case.label())
        }
    }

    @Test
    fun expectedPositionAgreesWithTheWeb() {
        for (case in cases("expected.json")) {
            val got = expectedPosition(case.session(), case.long("now"))
            assertEquals(case.getValue("expect").jsonPrimitive.double, got, TOLERANCE, case.label())
        }
    }

    @Test
    fun livenessAgreesWithTheWeb() {
        for (case in cases("liveness.json")) {
            val duration = case["duration"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.double
            assertEquals(case.getValue("expect").jsonPrimitive.boolean, isLive(case.session(), duration, case.long("now")), case.label())
        }
    }

    @Test
    fun followAgreesWithTheWeb() {
        for (case in cases("follow.json")) {
            val current = case.getValue("current").takeIf { it !is JsonNull }?.jsonObject?.toSession()
            val incoming = case.getValue("incoming").jsonObject.toSession()
            val expected = if (case.str("expect") == "current") current else incoming
            assertSame(expected, follow(current, incoming, case.str("me")!!), case.label())
        }
    }

    @Test
    fun decideAgreesWithTheWeb() {
        for (case in cases("decide.json")) {
            val got = decide(case.getValue("own").jsonObject.toScreenState(), case.session(), case.long("now"))
            val want = case.getValue("expect").jsonArray.map { it.jsonObject.toStep() }
            assertEquals(want.size, got.size, case.label())
            want.zip(got).forEach { (w, g) -> assertStep(w, g, case.label()) }
        }
    }

    @Test
    fun changeAgreesWithTheWeb() {
        for (case in cases("change.json")) {
            val expected =
                case.getValue("expect").takeIf { it !is JsonNull }?.jsonObject?.let { Noticed(Change.valueOf(it.str("change")!!.uppercase()), it.str("label")) }
            val got = changeOf(case.getValue("prev").jsonObject.toSession(), case.getValue("next").jsonObject.toSession())
            assertEquals(expected, got, case.label())
        }
    }

    private fun assertStep(
        want: Step,
        got: Step,
        label: String,
    ) {
        when {
            want is Step.Seek && got is Step.Seek -> assertEquals(want.pos, got.pos, TOLERANCE, label)
            want is Step.Open && got is Step.Open -> {
                assertEquals(want.pos, got.pos, TOLERANCE, label)
                assertEquals(want.copy(pos = 0.0), got.copy(pos = 0.0), label)
            }
            else -> assertEquals(want, got, label)
        }
    }
}

private const val TOLERANCE = 1e-6

private fun cases(file: String): List<JsonObject> {
    val found = assertNotNull(locateFixture(file), "$file not found above ${System.getProperty("user.dir")}")
    val all = Json.parseToJsonElement(found.readText()).jsonArray.map { it.jsonObject }
    assertTrue(all.isNotEmpty(), "$file holds no cases")
    return all
}

private fun locateFixture(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        File(dir, "web/test/fixtures/together/$name").takeIf { it.isFile }?.let { return it }
        dir = dir.parentFile
    }
    return null
}

private fun JsonObject.label() = "case: ${str("name")}"

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.double

private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

private fun JsonObject.session() = getValue("session").jsonObject.toSession()

private fun JsonObject.member(key: String) = getValue(key).jsonObject.toMember()

private fun JsonObject.toMember() = Member(str("screen")!!, str("label")!!)

private fun JsonObject.toSession() =
    Session(
        session = str("session")!!,
        set = str("set")!!,
        playing = getValue("playing").jsonPrimitive.boolean,
        pos = num("pos")!!,
        rate = num("rate")!!,
        at = long("at"),
        by = str("by")!!,
        members = getValue("members").jsonArray.map { it.jsonObject.toMember() },
        ended = getValue("ended").jsonPrimitive.boolean,
    )

private fun JsonObject.toPlayback() = Playback(str("set")!!, getValue("playing").jsonPrimitive.boolean, num("pos")!!, num("rate")!!)

private fun JsonObject.toChange() =
    PlaybackChange(
        set = str("set"),
        playing = (this["playing"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.boolean,
        pos = num("pos"),
        rate = num("rate"),
    )

private fun JsonObject.toScreenState() = ScreenState(str("set"), getValue("playing").jsonPrimitive.boolean, num("pos")!!, num("rate")!!)

private fun JsonObject.toStep(): Step =
    when (val kind = str("kind")) {
        "open" -> Step.Open(str("set")!!, num("pos")!!, getValue("playing").jsonPrimitive.boolean, num("rate")!!)
        "rate" -> Step.Rate(num("rate")!!)
        "seek" -> Step.Seek(num("pos")!!)
        "play" -> Step.Play
        "pause" -> Step.Pause
        else -> error("unknown step $kind")
    }
