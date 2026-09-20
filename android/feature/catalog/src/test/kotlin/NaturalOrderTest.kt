package catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Course folders are numbered by whoever built the course, and text order
 * reads those numbers one character at a time: it puts 10 before 2 and
 * turns an ordered curriculum into a shuffled one.
 */
class NaturalOrderTest {

    @Test
    fun numberedFoldersRunInNumberOrder() {
        val folders = listOf("10. Anhang", "2. Grundlagen", "1. Start")

        assertEquals(listOf("1. Start", "2. Grundlagen", "10. Anhang"), folders.sortedWith(NATURAL))
    }

    @Test
    fun aLeadingZeroDoesNotMakeANumberDifferent() {
        assertEquals(0, NATURAL.compare("Teil 007", "Teil 7"))
    }

    @Test
    fun textWithoutNumbersKeepsReadingAsText() {
        assertEquals(listOf("Alien", "Aliens", "Blade Runner"), listOf("Blade Runner", "Aliens", "Alien").sortedWith(NATURAL))
    }

    @Test
    fun aNumberInsideAWordIsStillANumber() {
        assertEquals(listOf("Tag1 Teil2", "Tag1 Teil10", "Tag2 Teil1"), listOf("Tag2 Teil1", "Tag1 Teil10", "Tag1 Teil2").sortedWith(NATURAL))
    }
}
