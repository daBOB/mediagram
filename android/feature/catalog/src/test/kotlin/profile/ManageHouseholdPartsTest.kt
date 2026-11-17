package catalog.profile

import model.Household
import model.Households
import model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Manage profiles by household — the web's `profile-manage-owner.test.ts`
 * over its `SPLIT` install, with a third household, the Loft, listed first:
 * andre owns the install and runs Home with Lea; Maja runs the Flat with Tim;
 * Ida runs the Loft.
 */
class ManageHouseholdPartsTest {
    private val andre = Profile("andre", "andre", admin = true, hasPin = true, owner = true)
    private val lea = Profile("lea", "Lea", kids = true, kidsAge = 12)
    private val maja = Profile("maja", "Maja", admin = true, hasPin = true, household = "flat")
    private val tim = Profile("tim", "Tim", kids = true, kidsAge = 6, parentId = "maja", household = "flat")
    private val ida = Profile("ida", "Ida", admin = true, hasPin = true, household = "loft")
    private val live = listOf(Household("loft", "Loft", "ida"), Household("flat", "Flat", "maja"), Household("home", "Home", "andre"))
    private val install = Households("home", live, listOf(andre, maja, tim, lea, ida))

    private fun panel(
        actor: Profile,
        here: List<Profile>,
        households: Households = install,
    ) = manageable(here, actor.id, null, install = households) as ManageUiState.Managing

    /** Each part as the headings it is drawn under, and its rows. */
    private fun ManageUiState.Managing.drawn() =
        parts().map { part ->
            listOfNotNull(part.name, GROWN_UPS.takeIf { part.showsGrownUps }, KIDS_SECTION.takeIf { part.showsKids }) to
                (part.grownUps + part.kids).map { it.name }
        }

    @Test
    fun theOwnerSeesThisDevicesHouseholdFirstThenTheOthersInTheLiveOrder() {
        val owner = panel(andre, listOf(andre, lea))
        assertEquals(
            listOf(
                listOf("Home", GROWN_UPS, KIDS_SECTION) to listOf("Lea"),
                listOf("Loft", GROWN_UPS, KIDS_SECTION) to listOf("Ida"),
                listOf("Flat", GROWN_UPS, KIDS_SECTION) to listOf("Maja", "Tim"),
            ),
            owner.drawn(),
        )
        // The owner adds in every household, each kid for one of its grown-ups.
        assertEquals(listOf(true to true, true to true, true to true), owner.parts().map { it.addsGrownUp to it.addsKid })
    }

    /** The one managing removed on another device: Who's managing? again, the owner's tile still there, labelled, at the row's end. */
    @Test
    fun whoIsManagingKeepsTheOwnersTileOnceTheManagerIsGone() {
        val asked = manageable(listOf(ida), maja.id, null, install = install) as ManageUiState.ChoosingActor
        assertEquals(andre, asked.owner)
        assertEquals(listOf("Ida", "andre"), asked.managers.map { it.name })
    }

    @Test
    fun aHouseholdWithNobodyInItKeepsItsPartToAddAGrownUp() {
        val owner = panel(andre, listOf(andre, lea), install.copy(everyone = listOf(andre, maja, tim, lea)))
        assertEquals(listOf("Home", "Loft", "Flat"), owner.parts().map { it.name })
        assertEquals(true to false, owner.parts()[1].let { it.addsGrownUp to it.addsKid })
    }

    /** The web's `personRow`, decided once for both screens: who each row is, its line, and what it opens — nothing for someone the owner may not change. */
    @Test
    fun eachRowSaysWhoItIsItsLineAndWhatItOpens() {
        val test = Profile("test", "test", hasPin = true)
        val kim = Profile("kim", "Kim", kids = true, kidsAge = 6, parentId = "test")
        val owner = panel(andre, listOf(andre, test, lea, kim), install.copy(everyone = install.everyone + test + kim))
        assertEquals(
            listOf(
                Triple("andre", "Owner · Admin · PIN set", RowOpens.YOU),
                Triple("test", "Grown-up · PIN set", RowOpens.GROWN_UP),
                Triple("Lea", "Kid · andre · FSK 12", RowOpens.KID),
                Triple("Kim", "Kid · test · FSK 6", null),
            ),
            owner.parts().first().people.map { Triple(it.profile.name, it.line, it.opens) },
        )
        // Elsewhere the owner changes everyone.
        assertEquals(listOf(RowOpens.GROWN_UP, RowOpens.KID), owner.parts().last().people.map { it.opens })
        // Anyone else: themselves, then whom they manage, lined among everyone here.
        val admin = panel(maja, listOf(maja, tim), install.copy(joined = "flat"))
        assertEquals(
            listOf(Triple("Maja", "Admin · PIN set", RowOpens.YOU), Triple("Tim", "Kid · Maja · FSK 6", RowOpens.KID)),
            admin.parts().single().people.map { Triple(it.profile.name, it.line, it.opens) },
        )
        assertEquals(listOf(true, true), listOf(owner, admin).map { it.parts().first().addsSomeone })
    }

    @Test
    fun aRowInAHouseholdTheListDoesNotNameStaysVisibleAfterTheNamedOnes() {
        val barn = Profile("ned", "Ned", household = "barn")
        val owner = panel(andre, listOf(andre, lea), install.copy(everyone = install.everyone + barn))
        assertEquals(listOf("Home", "Loft", "Flat", "barn"), owner.parts().map { it.name })
    }

    @Test
    fun anyoneElseManagesOnlyThisDevicesHouseholdUnnamed() {
        val admin = panel(maja, listOf(maja, tim), install.copy(joined = "flat"))
        assertEquals(listOf(listOf(GROWN_UPS, KIDS_SECTION) to listOf("Tim")), admin.drawn())
        assertEquals(true to true, admin.parts().single().let { it.addsGrownUp to it.addsKid })
        val parent = panel(maja.copy(admin = false), listOf(maja.copy(admin = false), tim), install.copy(joined = "flat"))
        assertEquals(listOf(listOf(KIDS_SECTION) to listOf("Tim")), parent.drawn())
    }

    @Test
    fun theOwnerOfOneHouseholdGetsTheOwnersViewToo() {
        val alone = Households("home", live.takeLast(1), listOf(andre, lea, maja.copy(household = "home", admin = false)))
        val owner = panel(andre, alone.everyone, alone)
        assertEquals(listOf(listOf("Home", GROWN_UPS, KIDS_SECTION) to listOf("Maja", "Lea")), owner.drawn())
    }
}
