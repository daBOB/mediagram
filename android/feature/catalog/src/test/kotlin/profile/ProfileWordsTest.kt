package catalog.profile

import model.Profile
import model.ProfileOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The words are the web's (`pin-prompt.js`, `profile-manage.js`, `profile-picker.js`), so a household reads one app, not two. */
class ProfileWordsTest {
    @Test
    fun everyRefusalIsTheWebsSentenceAndDoneIsNone() {
        assertNull(ProfileOutcome.Done.sentence())
        assertEquals("Wrong PIN.", ProfileOutcome.WrongPin.sentence())
        assertEquals("Too many wrong PINs. Try again in 42 s.", ProfileOutcome.Wait(42).sentence())
        assertEquals("That is not allowed.", ProfileOutcome.NotAllowed.sentence())
        assertEquals("This profile has no PIN yet. Choose it again to set one.", ProfileOutcome.NoPin.sentence())
        assertEquals("That profile is not here any more.", ProfileOutcome.NotFound.sentence())
        assertEquals("That was not accepted. Check the name and the PIN.", ProfileOutcome.Invalid.sentence())
        assertEquals("A profile with that name already exists.", ProfileOutcome.NameTaken.sentence())
    }

    @Test
    fun aGrownUpIsAskedItsPinOrToChooseOne() {
        assertEquals("andre’s PIN", pinTitleFor(Profile("a", "andre", hasPin = true)))
        assertEquals("Choose a PIN for test", pinTitleFor(Profile("t", "test")))
        assertEquals("The new PIN again", PinPrompt("Choose a PIN for test", newPin = true, confirming = true).heading)
        assertEquals("A PIN for Ann", newPinTitleFor("Ann"))
        assertEquals("A new PIN for Bea", resetPinTitleFor("Bea"))
    }

    @Test
    fun aKidsTileNamesItsOwnLimitAndAGrownUpsNothing() {
        assertEquals("Kids · FSK 6", Profile("m", "Mia", kids = true, kidsAge = 6).kidsTag)
        assertEquals("Kids · FSK 12", Profile("o", "TV kids", kids = true).kidsTag)
        assertNull(Profile("a", "andre").kidsTag)
    }

    @Test
    fun removingAGrownUpSaysItsKidsGoToo() {
        assertEquals("Remove Mia and everything they have watched?", removeQuestion(Profile("m", "Mia", kids = true)))
        assertEquals("Remove Bea, their kids, and everything they have watched?", removeQuestion(Profile("b", "Bea")))
    }

    /** The web says "a browser"; what gets past a PIN here is Android. */
    @Test
    fun theNoteSaysAPinIsNotALogin() {
        assertEquals(
            "Profiles keep your places and lists apart. A grown-up’s PIN keeps children out of it; " +
                "it is not a login, and someone who knows their way around Android can get past it.",
            PICKER_NOTE,
        )
        assertEquals("As Bea", managingAs("Bea"))
    }
}
