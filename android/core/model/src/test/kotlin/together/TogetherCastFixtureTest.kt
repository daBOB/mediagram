package model.together

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.HOME_HOUSEHOLD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the web's `web/test/fixtures/together/cast.json` against the Kotlin port of
 * `cast.js` and `cast-words.js`. The web decides: a case that passes only after
 * moving the port away from it is a bug in the port.
 */
class TogetherCastFixtureTest {
    private val words: JsonObject =
        Json.parseToJsonElement(locateFixture("cast.json")!!.readText()).jsonObject.getValue("words").jsonObject

    @Test
    fun wordsAgreeWithTheWeb() {
        val ported =
            mapOf(
                "playOnTv" to CastWords.PLAY_ON_TV,
                "asking" to CastWords.ASKING,
                "replace" to CastWords.REPLACE,
                "cancel" to CastWords.CANCEL,
                "noAnswer" to CastWords.NO_ANSWER,
                "leaveFirst" to CastWords.LEAVE_FIRST,
                "playingOnTv" to CastWords.PLAYING_ON_TV,
                "stop" to CastWords.STOP,
                "finished" to CastWords.FINISHED,
                "stopped" to CastWords.STOPPED,
                "libraryMissing" to CastWords.LIBRARY_MISSING,
                "profileUnknown" to CastWords.PROFILE_UNKNOWN,
                "joinFirst" to CastWords.JOIN_FIRST,
            )
        assertEquals(words.getValue("object").jsonObject.mapValues { it.value.jsonPrimitive.content }, ported)
    }

    @Test
    fun linesAgreeWithTheWeb() {
        val formats =
            mapOf<String, (String) -> String>(
                "busyQuestion" to ::busyQuestion,
                "refusedLine" to ::refusedLine,
                "castFrom" to ::castFrom,
                "barLine" to ::barLine,
                "ageReason" to ::ageReason,
                "timeReason" to ::timeReason,
                "sectionReason" to ::sectionReason,
                "noAnswerIn" to ::noAnswerIn,
            )
        for (case in words.getValue("lines").jsonArray.map { it.jsonObject }) {
            assertEquals(case.str("expect"), formats.getValue(case.str("fn")!!)(case.str("arg")!!), case.label())
        }
    }

    @Test
    fun offeredAgreesWithTheWeb() {
        for (case in cases("cast.json", "offered")) {
            val args = case.getValue("args").jsonObject
            val got = offered(args.str("id")!!, args.member("me"), args.getValue("own").jsonObject.toPlayback(), args.str("profile")!!, args.str("household")!!, args.long("now"))
            assertEquals(case.getValue("expect").jsonObject.toSession(), got, case.label())
        }
    }

    @Test
    fun answerAgreesWithTheWeb() {
        for (case in cases("cast.json", "answer")) {
            val tv = case.getValue("tv").jsonObject.let { TvState(it.str("screen")!!, it.str("busy"), it.str("refusal"), it.str("household")) }
            val want = case.getValue("expect").jsonObject
            val expected =
                when (val kind = want.str("kind")) {
                    "ignore" -> TvAnswer.Ignore
                    "accept" -> TvAnswer.Accept
                    "refuse" -> TvAnswer.Refuse(want.str("note")!!)
                    "busy" -> TvAnswer.Busy(want.str("note")!!)
                    else -> error("unknown answer $kind")
                }
            assertEquals(expected, answerOf(case.session(), tv, case.long("now")), case.label())
        }
    }

    @Test
    fun castElsewhereAgreesWithTheWeb() {
        for (case in cases("cast.json", "castElsewhere")) {
            assertEquals(case.getValue("expect").jsonPrimitive.boolean, castElsewhere(case.session(), case.str("household")), case.label())
        }
    }

    @Test
    fun movesAgreeWithTheWeb() {
        for (case in cases("cast.json", "moves")) {
            val args = case.getValue("args").jsonObject
            val s = case.session()
            val got =
                when (val move = case.str("move")) {
                    "busied" -> busied(s, args.str("tv")!!, args.str("note")!!)
                    "refusedBy" -> refusedBy(s, args.str("tv")!!, args.str("note")!!)
                    "replaced" -> replaced(s, args.str("me")!!, args.getValue("own").jsonObject.toPlayback(), args.long("now"))
                    "accepted" -> accepted(s, args.member("me"), args.long("now"))
                    "tracked" -> tracked(s, args.str("tv")!!, args.getValue("tracks").jsonObject.toCastTracks(), args.str("audio")!!, args.str("text")!!)
                    "chose" -> args.getValue("choice").jsonObject.let { chose(s, args.str("screen")!!, it.str("audio"), it.str("text")) }
                    else -> error("unknown move $move")
                }
            val expected = case["expect"]?.takeIf { it !is JsonNull }?.jsonObject?.toSession()
            assertEquals(expected, got, case.label())
            if (got != null) assertEquals(got, parseSession(writeSession(got)), "${case.label()} (round trip)")
        }
    }

    @Test
    fun endingAgreesWithTheWeb() {
        for (case in cases("cast.json", "ending")) {
            val expected = case.str("expect")?.let { Ending.valueOf(it.uppercase()) }
            assertEquals(expected, endingOf(case.session(), case.str("me")!!), case.label())
        }
    }

    @Test
    fun tracksStayUnderTheMessageLimit() {
        val phone = { n: Int -> Member("screen-$n", "Phone", remote = true) }
        val lists = { n: Int, p: String -> List(n) { Track("$p${it.toString().padStart(15, '0')}", "x".repeat(80)) } }
        var s = offered("s1", phone(0), Playback("film-1", true, 100.0, 1.0), "p1", HOME_HOUSEHOLD, 1000)
        s = accepted(s, Member("tv-1", "TV"), 2000)!!
        s = s.copy(members = s.members + phone(1) + phone(2))
        s = tracked(s, "tv-1", CastTracks(lists(30, "a"), lists(40, "t")), "a000000000000000", "t000000000000000")
        assertEquals(4, s.members.size)
        assertEquals(AUDIO_TRACKS_MAX, s.cast!!.tracks.audio.size)
        assertEquals(TEXT_TRACKS_MAX, s.cast!!.tracks.text.size)
        assertTrue(writeSession(s).length < 4096)
        assertEquals(s, parseSession(writeSession(s)))
    }

    @Test
    fun aTakenCastIsNotTakenTwice() {
        val on = offered("s1", Member("ph-1", "Phone"), Playback("film-1", true, 1.0, 1.0), "p1", HOME_HOUSEHOLD, 1000)
        val taken = accepted(on, Member("tv-1", "TV"), 2000)!!
        assertNull(accepted(taken, Member("tv-2", "TV"), 3000))
    }

    @Test
    fun writeSessionWithTracksIsByteIdenticalToTheWeb() {
        val case = cases("cast.json", "moves").single { it.str("name")!!.startsWith("quotes and non-ASCII") }
        val expected =
            "#mlib-together v=2\n" +
                """{"session":"s1","set":"film-1","playing":false,"pos":100,"rate":1,"at":1000,"by":"tv-1",""" +
                """"members":[{"screen":"ph-1","label":"Phone","remote":true},{"screen":"tv-1","label":"TV","remote":false}],""" +
                """"ended":false,"cast":{"to":"TV","from":"Phone","profile":"p1","household":"home","state":"on","tv":"tv-1","note":"",""" +
                """"tracks":{"audio":[{"id":"a1","label":"English"}],"text":[{"id":"t1","label":"Français \"forcé\""}]},""" +
                """"audio":"a1","text":"t1"}}"""
        assertEquals(expected, writeSession(case.getValue("expect").jsonObject.toSession()))
    }
}

private fun JsonObject.toCastTracks() = CastTracks(toTracks("audio"), toTracks("text"))

/** A cast from before casts carried a household is Home's, as the web reads it. */
internal fun JsonObject.toCast() =
    Cast(
        to = str("to")!!,
        from = str("from")!!,
        profile = str("profile")!!,
        household = str("household") ?: HOME_HOUSEHOLD,
        state = CastState.entries.single { it.word == str("state") },
        tv = str("tv")!!,
        note = str("note")!!,
        tracks = getValue("tracks").jsonObject.toCastTracks(),
        audio = str("audio")!!,
        text = str("text")!!,
    )
