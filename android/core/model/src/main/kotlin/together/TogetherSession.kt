package model.together

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/**
 * The watch-together session message, ported from `web/public/lib/together/session.js`
 * and held to the same fixtures (`web/test/fixtures/together/`). The web decides:
 * a case that passes only after moving this away from it is a bug here.
 *
 * The message is `#mlib-together v=1` on the first line and its JSON on the second.
 * Each session is one unpinned message in the library channel, edited in place,
 * and the last edit wins.
 */
const val TOGETHER_MARKER = "#mlib-together v=1"

data class Member(
    val screen: String,
    val label: String,
)

data class Session(
    val session: String,
    val set: String,
    val playing: Boolean,
    /** Seconds into [set] at the moment [at]. */
    val pos: Double,
    val rate: Double,
    /** The writer's wall clock, epoch milliseconds. */
    val at: Long,
    val by: String,
    val members: List<Member>,
    val ended: Boolean,
)

private fun JsonObject.named(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

/** An integral, non-negative JSON number, `1.0` included, as JS `Number.isInteger` has it. */
private fun JsonObject.instant(key: String): Long? {
    val p = (this[key] as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    val whole =
        p.longOrNull
            ?: p.doubleOrNull?.takeIf { it.isFinite() && it == Math.floor(it) && abs(it) < Long.MAX_VALUE.toDouble() }?.toLong()
    return whole?.takeIf { it >= 0 }
}

private fun JsonElement.member(): Member? {
    val o = this as? JsonObject ?: return null
    return Member(o.named("screen") ?: return null, o.named("label") ?: return null)
}

fun parseSession(message: String): Session? {
    val newline = message.indexOf('\n')
    if (newline < 0 || message.substring(0, newline) != TOGETHER_MARKER) return null
    val json = runCatching { Json.parseToJsonElement(message.substring(newline + 1)) }.getOrNull() as? JsonObject ?: return null
    val members = (json["members"] as? JsonArray)?.map { it.member() ?: return null } ?: return null
    return Session(
        session = json.named("session") ?: return null,
        set = json.named("set") ?: return null,
        playing = json.flag("playing") ?: return null,
        pos = json.number("pos")?.takeIf { it >= 0 } ?: return null,
        rate = json.number("rate")?.takeIf { it > 0 } ?: return null,
        at = json.instant("at") ?: return null,
        by = json.named("by") ?: return null,
        members = members,
        ended = json.flag("ended") ?: return null,
    )
}

fun writeSession(s: Session): String {
    val body =
        JsonObject(
            mapOf(
                "session" to JsonPrimitive(s.session),
                "set" to JsonPrimitive(s.set),
                "playing" to JsonPrimitive(s.playing),
                "pos" to JsonPrimitive(s.pos),
                "rate" to JsonPrimitive(s.rate),
                "at" to JsonPrimitive(s.at),
                "by" to JsonPrimitive(s.by),
                "members" to
                    JsonArray(
                        s.members.map {
                            JsonObject(mapOf("screen" to JsonPrimitive(it.screen), "label" to JsonPrimitive(it.label)))
                        },
                    ),
                "ended" to JsonPrimitive(s.ended),
            ),
        )
    return "$TOGETHER_MARKER\n$body"
}
