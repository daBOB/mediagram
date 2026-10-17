package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Holds [reportText], [cleanReportNote] and the words to the web's own `report-text.json`. */
class ReportTextFixtureTest {
    private val fixture: JsonObject =
        Json.parseToJsonElement(assertNotNull(locate(), "report-text.json not found").readText()).jsonObject

    private fun JsonObject.str(key: String): String? = getValue(key).takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    @Test
    fun agreesWithTheWebOnEveryCase() {
        val cases = fixture.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty(), "report-text.json holds no cases")
        for (case in cases.map { it.jsonObject }) {
            val f = case.getValue("facts").jsonObject
            val facts =
                ReportFacts(
                    reason = ReportReason.entries.single { it.id == f.str("reason") },
                    title = f.str("title")!!,
                    note = f.str("note"),
                    profile = f.str("profile"),
                    screen = f.str("screen")!!,
                    device = f.str("device"),
                    app = f.str("app")!!,
                    positionSeconds = f.getValue("position").jsonPrimitive.doubleOrNull,
                    played = f.str("played"),
                    held = f.getValue("held").jsonPrimitive.booleanOrNull!!,
                    error = f.str("error"),
                    setId = f.str("setId")!!,
                )
            assertEquals(case.str("text"), reportText(facts), case.str("name"))
        }
    }

    @Test
    fun notesCleanTheWayTheWebCleansThem() {
        for (n in fixture.getValue("notes").jsonArray.map { it.jsonObject }) {
            assertEquals(n.str("clean"), cleanReportNote(n.str("note")), n.str("name"))
        }
    }

    @Test
    fun reasonsWordsAndLimitAreTheFixtures() {
        val reasons = fixture.getValue("reasons").jsonArray.map { it.jsonObject.str("id")!! to it.jsonObject.str("words")!! }
        assertEquals(reasons, ReportReason.entries.map { it.id to it.words })
        val words = fixture.getValue("words").jsonObject
        assertEquals(words.str("button"), ReportWords.BUTTON)
        assertEquals(words.str("sent"), ReportWords.SENT)
        assertEquals(words.str("heading"), ReportWords.HEADING)
        assertEquals(words.str("note"), ReportWords.NOTE)
        assertEquals(words.str("send"), ReportWords.SEND)
        assertEquals(words.str("cancel"), ReportWords.CANCEL)
        assertEquals(words.str("failed"), ReportWords.FAILED)
        assertEquals(fixture.getValue("noteMax").jsonPrimitive.int, REPORT_NOTE_MAX)
    }

    @Test
    fun theClockReadsInWesternDigitsWhateverTheDeviceLanguage() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        try {
            val facts =
                ReportFacts(
                    reason = ReportReason.WONT_PLAY, title = "Dune", note = null, profile = null, screen = "TV", device = null,
                    app = "Android 0.126.0", positionSeconds = 4354.0, played = null, held = false, error = null, setId = "set1",
                )
            assertTrue("at 1:12:34" in reportText(facts), reportText(facts))
        } finally {
            Locale.setDefault(before)
        }
    }

    private fun locate(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "web/test/fixtures/report/report-text.json").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }
}
