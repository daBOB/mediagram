package ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the notes column goes — beside, as on the web, unless the window cannot spare it. */
class NotesPlacementTest {
    @Test
    fun aTabletOnItsSideKeepsTheColumnBesideThePicture() {
        assertEquals(NotesPlacement.BESIDE, notesPlacementFor(width = 1280.dp, height = 800.dp))
    }

    @Test
    fun anUprightWindowPutsTheNotesBelow() {
        assertEquals(NotesPlacement.BELOW, notesPlacementFor(width = 412.dp, height = 915.dp))
        assertEquals(NotesPlacement.BELOW, notesPlacementFor(width = 800.dp, height = 1280.dp))
    }

    @Test
    fun aPhoneOnItsSideGetsASheetOverThePicture() {
        assertEquals(NotesPlacement.OVER, notesPlacementFor(width = 915.dp, height = 412.dp))
    }
}
