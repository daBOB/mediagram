package player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.SubtitleTrackInfo
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the shared `choice-cases.json` fixture against [chooseSubtitles],
 * [toggleOn], [subtitleOptions] and [visibility] — the same cases
 * `web/public/lib/playback/subtitle-choice.js` is held to. The web is
 * authoritative: a case that only passes after changing this file's own
 * rules does not belong here.
 */
class SubtitleChoiceTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = locateSubtitlesFixture("choice-cases.json")
        assumeTrue("choice-cases.json not found above this module; is the web checkout present?", file != null)

        val cases = Json.parseToJsonElement(file!!.readText()).jsonArray
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val tracks = obj.getValue("tracks").jsonArray.map { it.jsonObject.toTrack() }
            val remembered = obj.text("remembered")
            val preferred = obj.text("preferred")
            val audioTag = obj.text("audioTag")
            val alang = obj.getValue("alang").jsonArray.map { it.jsonPrimitive.content }

            val audio = audioLanguage(audioTag, alang)
            assertEquals(obj.text("audio"), audio, "case: $name (audio)")

            val selection = chooseSubtitles(remembered, preferred, audio, tracks)
            assertEquals(obj.text("regular"), selection.regular, "case: $name (regular)")
            assertEquals(obj.text("forced"), selection.forced, "case: $name (forced)")

            val toggled = toggleOn(obj.text("last"), preferred, audio, tracks)
            assertEquals(obj.text("toggleOn"), toggled, "case: $name (toggleOn)")

            val rows = subtitleOptions(tracks, selection.regular).map { it.value }
            assertEquals(obj.getValue("pickerRows").jsonArray.map { it.jsonPrimitive.content }, rows, "case: $name (pickerRows)")

            val vis = visibility(tracks)
            assertEquals(obj.getValue("ccVisible").jsonPrimitive.boolean, vis.ccVisible, "case: $name (ccVisible)")
            assertEquals(obj.getValue("styleVisible").jsonPrimitive.boolean, vis.styleVisible, "case: $name (styleVisible)")
        }
    }
}

private fun JsonObject.toTrack() =
    SubtitleTrackInfo(
        track = 0,
        lang = getValue("lang").jsonPrimitive.content,
        forced = getValue("forced").jsonPrimitive.boolean,
        sdh = getValue("sdh").jsonPrimitive.boolean,
        label = getValue("label").jsonPrimitive.content,
    )

private fun JsonObject.text(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

/** Walks up from the working directory until it finds the web's subtitle fixtures, or gives up at the filesystem root. */
private fun locateSubtitlesFixture(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "web/test/fixtures/subtitles/$name")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}
