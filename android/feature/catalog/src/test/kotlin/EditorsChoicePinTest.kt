package catalog

import model.Kind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorsChoicePinTest {
    @Test
    fun aFilmCanBePinnedByAnAdult() = assertTrue(mayPinEditorsChoice(Kind.MOVIE, kidsProfile = false))

    @Test
    fun aKidsProfileCannotPinAnything() = assertFalse(mayPinEditorsChoice(Kind.MOVIE, kidsProfile = true))

    /** A pinned book would become Home's lead feature, and the web never offers a book that: books play from their cards. */
    @Test
    fun anAudiobookIsNeverOfferedTheEditorsChoicePin() = assertFalse(mayPinEditorsChoice(Kind.AUDIOBOOK, kidsProfile = false))
}
