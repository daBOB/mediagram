package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rule worth testing is that two rows are never the same: a film
 * routinely carries one language twice, once as 5.1 and once as a stereo
 * downmix, and a menu reading "German / German" is not a menu.
 *
 * Two-letter tags throughout (`de`, `en`), not ffprobe's three-letter
 * `deu`/`eng`: media3's `Format.language` already shortens both to the
 * same two-letter form by the time it reaches this app, so a three-letter
 * tag is not a shape this function is ever actually handed.
 */
class AudioOptionsTest {

    private fun facts(
        groupIndex: Int = 0,
        trackIndex: Int = 0,
        language: String? = "en",
        label: String? = null,
        channelCount: Int = 2,
        sampleMimeType: String? = "audio/mp4a-latm",
        isSelected: Boolean = false,
    ) = AudioTrackFacts(groupIndex, trackIndex, language, label, channelCount, sampleMimeType, isSelected)

    // -- channel layouts --

    @Test
    fun theLayoutsEveryoneKnowsAreNamed() {
        assertEquals("mono", channelLayoutLabel(1))
        assertEquals("stereo", channelLayoutLabel(2))
        assertEquals("5.1", channelLayoutLabel(6))
        assertEquals("7.1", channelLayoutLabel(8))
    }

    @Test
    fun anUnusualCountIsStatedRatherThanGuessedAt() {
        assertEquals("3ch", channelLayoutLabel(3))
        assertEquals("12ch", channelLayoutLabel(12))
    }

    @Test
    fun aCountNobodySuppliedSaysNothing() {
        assertEquals("", channelLayoutLabel(0))
        assertEquals("", channelLayoutLabel(-1))
    }

    // -- rows --

    @Test
    fun aLanguageHowManyChannelsAndWhatItIs() {
        val row = facts(language = "de", channelCount = 6, sampleMimeType = "audio/ac3")
        assertEquals("German · 5.1 · ac3", audioTrackLabel(row, 1))
    }

    @Test
    fun codecsAreNamedTheWayFfprobeNamesThem() {
        assertEquals("eac3", audioTrackLabel(facts(sampleMimeType = "audio/eac3"), 1).substringAfterLast(" · "))
        assertEquals("dts", audioTrackLabel(facts(sampleMimeType = "audio/vnd.dts"), 1).substringAfterLast(" · "))
        // DTS Express carries its profile in a MIME suffix, which ffprobe
        // keeps out of `codec_name` too — both still just "dts".
        assertEquals("dts", audioTrackLabel(facts(sampleMimeType = "audio/vnd.dts.hd;profile=lbr"), 1).substringAfterLast(" · "))
        assertEquals("truehd", audioTrackLabel(facts(sampleMimeType = "audio/true-hd"), 1).substringAfterLast(" · "))
        // Unmapped subtypes are stated as themselves rather than guessed at.
        assertEquals("alac", audioTrackLabel(facts(sampleMimeType = "audio/alac"), 1).substringAfterLast(" · "))
    }

    @Test
    fun theSameLanguageTwiceIsStillTwoDistinguishableRows() {
        val surround = audioTrackLabel(facts(language = "de", channelCount = 6, sampleMimeType = "audio/ac3"), 1)
        val stereo = audioTrackLabel(facts(trackIndex = 1, language = "de", channelCount = 2, sampleMimeType = "audio/mp4a-latm"), 2)
        assertNotEquals(surround, stereo)
    }

    @Test
    fun aStreamThatNamedItselfGetsToKeepTheName() {
        val row = facts(language = "en", label = "Commentary")
        assertEquals("English · Commentary · stereo · aac", audioTrackLabel(row, 1))
    }

    @Test
    fun anUntaggedStreamIsCalledByItsNumberNotByUnd() {
        assertEquals("Track 3 · stereo · aac", audioTrackLabel(facts(trackIndex = 2, language = null), 3))
        assertEquals("Track 1 · stereo · aac", audioTrackLabel(facts(language = "und"), 1))
    }

    @Test
    fun aStreamThatSaysNothingAtAllStillGetsARow() {
        val row = facts(language = null, sampleMimeType = null, channelCount = 0)
        assertEquals("Track 1", audioTrackLabel(row, 1))
    }

    @Test
    fun labelsAreEnglishRegardlessOfDeviceLocale() {
        // Not `Locale.setDefault` + a German expectation: the label is
        // fixed English on purpose (matching the web and this app's own
        // stats overlay), so nothing here should depend on the JVM's
        // default locale to begin with.
        assertEquals("German", audioTrackLabel(facts(language = "de", sampleMimeType = null, channelCount = 0), 1))
    }

    // -- matching a remembered language --

    private val twoTracks = listOf(
        facts(trackIndex = 0, language = "de", channelCount = 6, sampleMimeType = "audio/ac3", isSelected = true),
        facts(trackIndex = 1, language = "en", channelCount = 2, sampleMimeType = "audio/mp4a-latm"),
    )

    @Test
    fun findsTheTrackCarryingIt() {
        assertEquals(1, audioTrackForLanguage(twoTracks, "en"))
        assertEquals(0, audioTrackForLanguage(twoTracks, "de"))
    }

    @Test
    fun ignoringCaseAndStrayWhitespace() {
        assertEquals(1, audioTrackForLanguage(twoTracks, " EN "))
    }

    @Test
    fun aLanguageThisFileDoesNotCarryIsNothingNotTheFirstTrack() {
        assertNull(audioTrackForLanguage(twoTracks, "fr"))
    }

    @Test
    fun andNothingRememberedIsNothing() {
        for (language in listOf(null, "", "  ")) {
            assertNull(audioTrackForLanguage(twoTracks, language))
        }
    }

    @Test
    fun anOrdinalIsNeverWhatIsMatched() {
        // A stored "1" would be German in one release and a commentary in the next.
        assertNull(audioTrackForLanguage(twoTracks, "1"))
    }

    // -- the menu itself --

    @Test
    fun oneTrackIsNoMenuAtAll() {
        assertEquals(emptyList(), audioOptions(listOf(facts()), null))
        assertEquals(emptyList(), audioOptions(emptyList(), null))
    }

    @Test
    fun theRememberedLanguageIsMarkedSelectedEvenOverExoPlayersOwnPick() {
        val options = audioOptions(twoTracks, "en")
        assertEquals(listOf(false, true), options.map { it.selected })
    }

    @Test
    fun nothingRememberedLeavesExoPlayersOwnSelectionMarked() {
        // Track 0 is what `isSelected` says the player already chose —
        // never a guess of this app's own; see `AudioChoiceController`.
        val options = audioOptions(twoTracks, null)
        assertEquals(listOf(true, false), options.map { it.selected })
    }

    @Test
    fun aLanguageThisFileDroppedAlsoFallsBackToExoPlayersOwnSelection() {
        val options = audioOptions(twoTracks, "fr")
        assertEquals(listOf(true, false), options.map { it.selected })
    }

    @Test
    fun eachRowCarriesWhereToFindItInARealOverride() {
        val options = audioOptions(twoTracks, "en")
        assertEquals(0 to 1, options[1].groupIndex to options[1].trackIndex)
    }

    // -- what is worth saving --

    @Test
    fun aRealLanguageIsUsable() {
        assertTrue(isUsableAudioLanguage("en"))
    }

    @Test
    fun blankAndUndAreNotUsable() {
        for (language in listOf(null, "", "  ", "und", "UND")) {
            assertFalse(isUsableAudioLanguage(language))
        }
    }
}
