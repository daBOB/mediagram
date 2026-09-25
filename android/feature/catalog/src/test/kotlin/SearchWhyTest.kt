package catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchWhyTest {

    @Test
    fun aTitleMatchNeedsNoExplaining() {
        assertNull(searchWhy("title"))
    }

    @Test
    fun everyOtherFieldSaysWhereItMatched() {
        assertEquals("matched the series or course", searchWhy("show"))
        assertEquals("matched the chapter", searchWhy("chap"))
        assertEquals("matched the folder", searchWhy("path"))
        assertEquals("found in the summary", searchWhy("summary"))
    }

    @Test
    fun anUnrecognisedFieldSaysNothingRatherThanGuessing() {
        assertNull(searchWhy("something-a-newer-core-added"))
    }
}
