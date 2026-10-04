package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The household rule, row by row of the spec's own table — `profile-rules.json` holds the rest. */
class ProfileRolesTest {
    private val andre = Profile("a", "andre", admin = true)
    private val bea = Profile("b", "Bea")
    private val mia = Profile("m", "Mia", kids = true, kidsAge = 6, parentId = "a")
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    private val tvKids = Profile("o", "TV kids", kids = true)
    private val household = listOf(andre, bea, mia, tom, tvKids)

    @Test
    fun aKidBelongsToItsParentOrElseToTheAdmin() {
        assertEquals("b", household.ownerOf(tom))
        assertEquals("a", household.ownerOf(tvKids))
        assertEquals("a", household.ownerOf(tom.copy(parentId = "removed-here")))
        assertNull(listOf(bea, tvKids).ownerOf(tvKids))
    }

    /** A view that says a kid is the admin — a claim left on a row another device made a kid — names no owner. */
    @Test
    fun aKidSaidToBeTheAdminOwnsNothingAndIsNotProtected() {
        val claimed = Profile("x", "Old admin", kids = true, admin = true)
        val household = listOf(bea, claimed, tvKids)
        assertNull(household.ownerOf(tvKids))
        assertFalse(household.allowed("x", RoleAction.CREATE_KID, null))
    }

    @Test
    fun onlyTheAdminAddsGrownUpsAndEveryGrownUpAddsKids() {
        assertTrue(household.allowed("a", RoleAction.CREATE_GROWN_UP, null))
        assertFalse(household.allowed("b", RoleAction.CREATE_GROWN_UP, null))
        assertTrue(household.allowed("b", RoleAction.CREATE_KID, null))
    }

    @Test
    fun aParentManagesItsOwnKidsAndNoOneElses() {
        assertTrue(household.allowed("b", RoleAction.SET_KIDS_AGE, "t"))
        assertTrue(household.allowed("b", RoleAction.REMOVE, "t"))
        assertFalse(household.allowed("a", RoleAction.SET_KIDS_AGE, "t"))
        assertTrue(household.allowed("a", RoleAction.REMOVE, "o"))
    }

    @Test
    fun nobodyRemovesTheAdminNotEvenTheAdmin() {
        assertFalse(household.allowed("a", RoleAction.REMOVE, "a"))
        assertFalse(household.allowed("b", RoleAction.REMOVE, "a"))
        assertTrue(household.allowed("a", RoleAction.REMOVE, "b"))
    }

    @Test
    fun aGrownUpSetsItsOwnPinAndTheAdminAnyGrownUps() {
        assertTrue(household.allowed("b", RoleAction.SET_PIN, "b"))
        assertFalse(household.allowed("b", RoleAction.SET_PIN, "a"))
        assertTrue(household.allowed("a", RoleAction.SET_PIN, "b"))
        assertFalse(household.allowed("a", RoleAction.SET_PIN, "m"))
    }

    @Test
    fun aKidOrSomeoneUnknownMayDoNothing() {
        RoleAction.entries.forEach { action ->
            assertFalse(household.allowed("m", action, "m"), "kid: $action")
            assertFalse(household.allowed("nobody", action, "m"), "unknown: $action")
        }
    }

    @Test
    fun aKidWithNoLimitRecordedSeesUpToTwelve() {
        assertEquals(12, tvKids.kidsLimit)
        assertEquals(6, mia.kidsLimit)
        assertEquals(12, tvKids.copy(kidsAge = 7).kidsLimit)
    }

    @Test
    fun eachMarkReadsAsTheAgeItIsForKidsFrom() {
        val snapshot = WatchSnapshot.Empty.copy(kids = listOf("a", "b"), kidsFromSix = listOf("b"))
        assertEquals(mapOf("a" to 12, "b" to 6), snapshot.kidsMarks)
    }
}
