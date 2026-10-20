package catalog.profile

import model.HouseholdWords
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
    }

    /** Names are compared across every household on the install, so the web's wording says so. */
    @Test
    fun aTakenNameIsUsedOnThisInstall() {
        assertEquals(HouseholdWords.PROFILE_NAME_TAKEN, ProfileOutcome.NameTaken.sentence())
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
    fun profilesIndexLineNamesWhoIsWatchingThenHowManyThereAre() {
        assertEquals("Bea · 3 profiles", profilesLine(Profile("b", "Bea"), 3))
        assertEquals("Ann · 1 profile", profilesLine(Profile("a", "Ann", admin = true), 1))
        assertEquals("3 profiles", profilesLine(null, 3))
        // A kid's page shows nobody else, so its line counts nobody either.
        assertEquals("Tom", profilesLine(Profile("t", "Tom", kids = true), 3))
    }

    @Test
    fun aKidsTileNamesItsOwnLimitAndAGrownUpsNothing() {
        assertEquals("Kids · FSK 6", Profile("m", "Mia", kids = true, kidsAge = 6).kidsTag)
        assertEquals("Kids · FSK 12", Profile("o", "TV kids", kids = true).kidsTag)
        assertNull(Profile("a", "andre").kidsTag)
    }

    /** The web's `roleLine`: the sections someone is not shown, in the nav's order, a key this build does not know left out — never for whoever runs the household. */
    @Test
    fun theEveryoneLineSaysWhichSectionsSomeoneIsNotShown() {
        val andre = Profile("a", "andre", admin = true, owner = true, hasPin = true, hiddenSections = listOf("anime"))
        val bea = Profile("b", "Bea", hiddenSections = listOf("later", "movies", "tutorials"))
        val maja = Profile("m", "Maja", kids = true, kidsAge = 6, parentId = "b", dailyLimit = 45, hiddenSections = listOf("tutorials", "anime"))
        val tom = Profile("t", "Tom", kids = true, parentId = "b")
        assertEquals(
            listOf("Owner · Admin · PIN set", "Grown-up · No PIN · hides Movies, Tutorials", "Kid · Bea · FSK 6 · 45 min · hides Anime, Tutorials", "Kid · Bea · FSK 12"),
            everyoneHere(listOf(andre, bea, maja, tom)).map { it.second },
        )
    }

    /** The web's `roleLine`: the owner's line starts with the word, then the role it has in its own household. */
    @Test
    fun theOwnerIsSaidFirstInEveryoneHere() {
        val andre = Profile("a", "andre", admin = true, owner = true, hasPin = true)
        val bea = Profile("b", "Bea", admin = true, hasPin = true)
        assertEquals(listOf("Owner · Admin · PIN set", "Admin · PIN set"), everyoneHere(listOf(andre, bea)).map { it.second })
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
