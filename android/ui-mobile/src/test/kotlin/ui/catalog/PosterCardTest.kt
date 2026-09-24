package ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A library read from a channel's pinned index carries no artwork at all,
 * so what stands in for a poster is what a viewer actually scans. Only the
 * mapping is testable here — this module's tests are plain JVM ones with no
 * Compose test rule.
 */
class PosterCardTest {
    @Test
    fun twoWordsGiveTwoLetters() {
        assertEquals("3R", initialsOf("30 Rock"))
        assertEquals("BN", initialsOf("Brooklyn Nine-Nine"))
    }

    @Test
    fun aOneWordTitleGivesTheOneLetterItHas() {
        assertEquals("P", initialsOf("Psych"))
    }

    /**
     * Titles in this library start with brackets, hashes and digits —
     * "(500) Days of Summer", "#Zeitgeist" — and a card showing "(#" tells
     * a viewer nothing.
     */
    @Test
    fun punctuationIsSkippedForTheLetterBehindIt() {
        assertEquals("5D", initialsOf("(500) Days of Summer"))
        assertEquals("ZE", initialsOf("#Zeitgeist ep"))
    }

    @Test
    fun aTitleWithNothingToInitialiseStillShowsSomething() {
        assertEquals("?", initialsOf("—"))
        assertEquals("?", initialsOf(""))
    }
}
