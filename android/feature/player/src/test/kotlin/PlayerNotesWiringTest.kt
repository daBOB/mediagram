package player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import model.markdown.Block
import org.junit.After
import playback.SummarySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers [PlayerViewModel.notes] — `showSummary` in the web's `player.js`. */
class PlayerNotesWiringTest {

    private val lesson = fakeMediaSet("l1", kind = Kind.TUTORIAL).copy(hasSummary = true)
    private val film = fakeMediaSet("f1", kind = Kind.MOVIE).copy(hasSummary = true)
    private val bare = fakeMediaSet("b1", kind = Kind.TUTORIAL)
    private val catalog = FakeCatalogRepository(listOf(lesson, film, bare).associateBy { it.setId })

    private class Summaries(private val texts: Map<String, String>) : SummarySource {
        val asked = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun load(setId: String): String? {
            asked += setId
            gate?.await()
            return texts[setId]
        }
    }

    private val summaries = Summaries(mapOf("l1" to "### Lektion\n\nText", "f1" to "Über den Film", "b1" to "never asked"))

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aLessonsNotesOpenByThemselves() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog, summarySource = summaries)
        vm.open("l1")
        advanceUntilIdle()

        val notes = vm.notes.value!!
        assertTrue(notes.open)
        assertEquals(Block.Heading::class, notes.blocks.first()::class)
    }

    @Test
    fun aFilmsNotesWaitForTheButton() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog, summarySource = summaries)
        vm.open("f1")
        advanceUntilIdle()

        assertFalse(vm.notes.value!!.open)
        vm.toggleNotes()
        assertTrue(vm.notes.value!!.open)
    }

    @Test
    fun aTitleTheIndexSaysHasNoSummaryIsNotAsked() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog, summarySource = summaries)
        vm.open("b1")
        advanceUntilIdle()

        assertNull(vm.notes.value)
        assertTrue(summaries.asked.isEmpty())
    }

    @Test
    fun switchingTitleDropsThePanelAndTheNewTitleDecidesAgain() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog, summarySource = summaries)
        vm.open("f1")
        advanceUntilIdle()
        vm.toggleNotes()

        vm.open("l1")
        advanceUntilIdle()
        assertTrue(vm.notes.value!!.open)
        assertEquals("Lektion", ((vm.notes.value!!.blocks.first() as Block.Heading).spans.single() as model.markdown.Span.Text).text)

        vm.open("f1")
        advanceUntilIdle()
        assertFalse(vm.notes.value!!.open)
    }

    /** The viewer moved on while the first title's notes were still loading; they must not land on the second. */
    @Test
    fun aLoadOvertakenByAnotherTitleIsDropped() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        summaries.gate = gate
        val vm = buildViewModel(catalogRepository = catalog, summarySource = summaries)
        vm.open("l1")
        advanceUntilIdle()
        vm.open("b1")
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(vm.notes.value)
    }
}
