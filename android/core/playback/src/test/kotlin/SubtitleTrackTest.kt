package playback

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [REAL_SAMPLE] is a real subtitle row from a course lesson's `library.db`
 * (`assets` table, `kind='subtitle'`, `lang='und'` — the uploader's own
 * Whisper transcript, tagged `und` because the course never named a spoken
 * language): proof that this app's chosen parsing path — media3's own
 * `WebvttParser`, see `SubtitleTrack.kt` — actually turns what the uploader
 * writes into cues, not just a hand-built fixture shaped to fit it.
 *
 * Robolectric, not the bare android.jar: `WebvttParser` skips the header
 * with `while (!TextUtils.isEmpty(readLine()))`, and the stubbed
 * `isEmpty` answers `false` forever, so that loop never ends without it.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleTrackTest {

    private val realSample = """
        WEBVTT

        00:00:00.000 --> 00:00:03.000
         Herzlich willkommen zu deiner finanziellen Neuausrichtung.

        00:00:03.000 --> 00:00:06.200
         Wir freuen uns wahnsinnig, dich auf deiner Reise bekleiden zu dürfen,

        00:00:06.200 --> 00:00:09.560
         wünschen dir dabei einen Mega-Erfolg und ganz, ganz viel Spaß.

    """.trimIndent()

    @Test
    fun aRealTranscriptTurnsIntoOneCuePerBlock() {
        val cues = parseWebVttCues(realSample)
        assertEquals(3, cues.size)
    }

    @Test
    fun timesAreReadInMilliseconds() {
        val cues = parseWebVttCues(realSample)
        assertEquals(0L, cues[0].startMs)
        assertEquals(3_000L, cues[0].endMs)
        assertEquals(3_000L, cues[1].startMs)
        assertEquals(6_200L, cues[1].endMs)
    }

    @Test
    fun theTextItselfSurvives() {
        val cues = parseWebVttCues(realSample)
        assertTrue(cues[0].text.contains("Herzlich willkommen"))
        assertTrue(cues[2].text.contains("Mega-Erfolg"))
    }

    @Test
    fun aFileWithNoCuesAtAllParsesToNothing() {
        assertEquals(emptyList(), parseWebVttCues("WEBVTT\n\n"))
    }

    @Test
    fun aMultiLineCueJoinsIntoOneBlock() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            first line
            second line

        """.trimIndent()
        val cues = parseWebVttCues(vtt)
        assertEquals(1, cues.size)
        assertEquals("first line\nsecond line", cues[0].text)
    }
}
