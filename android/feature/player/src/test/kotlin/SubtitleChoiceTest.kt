package player

import kotlin.test.Test
import kotlin.test.assertEquals

/** Ported from the web's default rule (`transport.js`'s `offerSubtitles`). */
class SubtitleChoiceTest {

    private val twoLanguages = listOf("de", "en")

    // -- the default rule --

    @Test
    fun aRememberedOffIsRespectedEvenWithLanguagesOnOffer() {
        assertEquals(SUBTITLES_OFF, chooseSubtitleLanguage(twoLanguages, "off"))
    }

    @Test
    fun aRememberedLanguageStillOnOfferIsRestored() {
        assertEquals("en", chooseSubtitleLanguage(twoLanguages, "en"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals("en", chooseSubtitleLanguage(twoLanguages, "EN"))
    }

    @Test
    fun aRememberedLanguageThisFileHasLostFallsBackToTheFirstTrack() {
        assertEquals("de", chooseSubtitleLanguage(twoLanguages, "fr"))
    }

    @Test
    fun nothingRememberedFallsBackToTheFirstTrack() {
        assertEquals("de", chooseSubtitleLanguage(twoLanguages, null))
    }

    @Test
    fun aFileWithNoSubtitlesAtAllIsOffRegardlessOfWhatIsRemembered() {
        assertEquals(SUBTITLES_OFF, chooseSubtitleLanguage(emptyList(), "de"))
        assertEquals(SUBTITLES_OFF, chooseSubtitleLanguage(emptyList(), null))
    }

    // -- the rows the sheet offers --

    @Test
    fun offPlusOneRowPerLanguageChosenMarkedSelected() {
        val options = subtitleOptions(twoLanguages, chosen = "en")
        assertEquals(listOf(SUBTITLES_OFF, "de", "en"), options.map { it.value })
        assertEquals(listOf(false, false, true), options.map { it.selected })
    }

    @Test
    fun offItselfCanBeMarkedSelected() {
        val options = subtitleOptions(twoLanguages, chosen = SUBTITLES_OFF)
        assertEquals(true, options.first { it.value == SUBTITLES_OFF }.selected)
    }

    @Test
    fun aFileWithNoSubtitlesOffersNoRowsAtAllNotEvenOff() {
        // "Off" alone is not a choice — the same rule the sheet reads to
        // hide the section entirely.
        assertEquals(emptyList(), subtitleOptions(emptyList(), chosen = SUBTITLES_OFF))
    }

    @Test
    fun anUntaggedTrackIsLabelledSubtitlesNotByANumber() {
        val options = subtitleOptions(listOf("und"), chosen = "und")
        assertEquals("Subtitles", options.first { it.value == "und" }.label)
    }

    @Test
    fun aTaggedTrackIsLabelledInEnglish() {
        val options = subtitleOptions(listOf("de"), chosen = SUBTITLES_OFF)
        assertEquals("German", options.first { it.value == "de" }.label)
    }
}
