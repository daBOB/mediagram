package model

import java.util.Locale
import kotlin.math.floor

/**
 * The words a broken-video report reaches the library's keeper in: a reminder in the account's
 * own Saved Messages. The web's `report-text.js` is the reference; this port is held to its
 * `web/test/fixtures/report/report-text.json`, so a change to either side fails the other's suite.
 */
enum class ReportReason(val id: String, val words: String) {
    WONT_PLAY("wont-play", "Won't play"),
    PICTURE_SOUND("picture-sound", "Picture or sound wrong"),
    SUBTITLES("subtitles", "Subtitles wrong"),
    WRONG_TITLE("wrong-title", "Wrong title or poster"),
}

object ReportWords {
    const val BUTTON = "Report"
    const val SENT = "Reported"
    const val HEADING = "What's wrong?"
    const val NOTE = "Anything else? (optional)"
    const val SEND = "Send"
    const val CANCEL = "Cancel"
    const val FAILED = "Couldn't send. Try again later."
}

const val REPORT_NOTE_MAX = 200

/** What a report knows when it is sent; [positionSeconds], [played] and [error] may all be unknown. */
data class ReportFacts(
    val reason: ReportReason,
    val title: String,
    val note: String?,
    val profile: String?,
    val screen: String,
    val device: String?,
    val app: String,
    val positionSeconds: Double?,
    val played: String?,
    val held: Boolean,
    val error: String?,
    val setId: String,
)

// (?U) so \s matches the Unicode spaces JavaScript's \s does (NBSP, em space).
private val WHITESPACE = Regex("""(?U)\s+""")

/** `h:mm:ss`, the hour unpadded, as a player's clock reads past an hour; in the web's digits whatever the device's language. */
private fun clock(seconds: Double): String {
    val s = floor(seconds).toLong()
    return "%d:%02d:%02d".format(Locale.ROOT, s / 3600, s % 3600 / 60, s % 60)
}

/** One line, at most [REPORT_NOTE_MAX] characters; empty when nothing was said. */
fun cleanReportNote(note: String?): String = (note ?: "").replace(WHITESPACE, " ").trim().take(REPORT_NOTE_MAX).trimEnd()

fun reportText(facts: ReportFacts): String {
    val note = cleanReportNote(facts.note)
    val who =
        listOf(
            facts.profile,
            if (!facts.device.isNullOrEmpty()) "${facts.screen} (${facts.device})" else facts.screen,
            facts.app,
            facts.positionSeconds?.let { "at ${clock(it)}" },
        ).filter { !it.isNullOrEmpty() }
    val how =
        listOf(
            facts.played?.takeIf { it.isNotEmpty() }?.let { "Played: $it${if (facts.held) ", from cache" else ""}" },
            facts.error?.takeIf { it.isNotEmpty() }?.let { "Error: $it" },
        ).filterNotNull()
    return listOfNotNull(
        "#mlib-report",
        "${facts.reason.words}: ${facts.title}",
        if (note.isNotEmpty()) "\"$note\"" else null,
        who.joinToString(" · "),
        if (how.isNotEmpty()) how.joinToString(" · ") else null,
        "Check: mediagram verify --full ${facts.setId}",
    ).joinToString("\n")
}
