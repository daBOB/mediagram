package model.together

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The cast carried by a session message, ported from `castOf` and `writeSession` in
// `web/public/lib/together/session.js`; the key order below is the web's.

data class Track(
    val id: String,
    val label: String,
)

data class CastTracks(
    val audio: List<Track> = emptyList(),
    val text: List<Track> = emptyList(),
)

enum class CastState(
    val word: String,
) {
    OFFER("offer"),
    BUSY("busy"),
    REFUSED("refused"),
    REPLACE("replace"),
    ON("on"),
}

/** A phone's offer to play the session on the TV, and the TV's answer to it. */
data class Cast(
    val to: String = "TV",
    val from: String,
    val profile: String,
    val state: CastState,
    val tv: String = "",
    val note: String = "",
    val tracks: CastTracks = CastTracks(),
    val audio: String = "",
    val text: String = "",
)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.track(): Track? {
    val o = this as? JsonObject ?: return null
    return Track(o.named("id") ?: return null, o.named("label") ?: return null)
}

private fun JsonElement?.tracks(): List<Track>? = (this as? JsonArray)?.map { it.track() ?: return null }

internal fun JsonElement?.cast(): Cast? {
    val o = this as? JsonObject ?: return null
    val tracks = o["tracks"] as? JsonObject ?: return null
    return Cast(
        to = o.named("to") ?: return null,
        from = o.named("from") ?: return null,
        profile = o.named("profile") ?: return null,
        state = CastState.entries.firstOrNull { it.word == o.text("state") } ?: return null,
        tv = o.text("tv") ?: return null,
        note = o.text("note") ?: return null,
        tracks = CastTracks(tracks["audio"].tracks() ?: return null, tracks["text"].tracks() ?: return null),
        audio = o.text("audio") ?: return null,
        text = o.text("text") ?: return null,
    )
}

private fun trackJson(t: Track) = JsonObject(mapOf("id" to JsonPrimitive(t.id), "label" to JsonPrimitive(t.label)))

internal fun castJson(c: Cast) =
    JsonObject(
        mapOf(
            "to" to JsonPrimitive(c.to),
            "from" to JsonPrimitive(c.from),
            "profile" to JsonPrimitive(c.profile),
            "state" to JsonPrimitive(c.state.word),
            "tv" to JsonPrimitive(c.tv),
            "note" to JsonPrimitive(c.note),
            "tracks" to
                JsonObject(
                    mapOf(
                        "audio" to JsonArray(c.tracks.audio.map(::trackJson)),
                        "text" to JsonArray(c.tracks.text.map(::trackJson)),
                    ),
                ),
            "audio" to JsonPrimitive(c.audio),
            "text" to JsonPrimitive(c.text),
        ),
    )
