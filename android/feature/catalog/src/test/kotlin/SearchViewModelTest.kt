package catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.PersonHit
import org.junit.After
import uniffi.mediagram_core.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [SearchViewModel] debounces typing and ranks through [data.CatalogRepository],
 * leaving the join onto a [model.MediaSet] to whoever already holds the
 * catalog — mirroring the web's own search view (`search-view.js`), and
 * never rebuilding the catalog itself just to answer a query.
 */
class SearchViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun anEmptyFieldStaysIdle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(FakeCatalogRepository())

        vm.state.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            vm.setQuery("")
            advanceTimeBy(300)
            runCurrent()
            expectNoEvents()
        }
    }

    /**
     * Three keystrokes inside the debounce window are one search, not three —
     * the same 200 ms pause the web's own search box waits out before it
     * moves the address bar.
     */
    @Test
    fun typingQuicklyRanksOnlyTheSettledQuery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeCatalogRepository(movies = 1)
        val vm = SearchViewModel(repository)

        vm.state.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            vm.setQuery("s")
            advanceTimeBy(50)
            vm.setQuery("st")
            advanceTimeBy(50)
            vm.setQuery("steuer")
            advanceTimeBy(250)
            runCurrent()

            assertEquals(SearchUiState.Ready(emptyList()), awaitItem())
        }
        assertEquals(listOf("steuer"), repository.searches)
    }

    @Test
    fun aSettledQueryAnswersTheCoresOwnHits() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val hit = SearchHit(setId = "movie-0", matched = "summary", excerpt = "…mentioned here…")
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1, searchHits = listOf(hit)))

        vm.state.test {
            awaitItem()
            vm.setQuery("steuer")
            advanceTimeBy(250)
            runCurrent()

            assertEquals(SearchUiState.Ready(listOf(hit)), awaitItem())
        }
    }

    /** People ride alongside the hits a settled query answers, both from the same round. */
    @Test
    fun aSettledQueryAlsoAnswersTheCoresOwnPeople() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val person = PersonHit(1, "Bryan Cranston", null, listOf("tmdb-movie-1"))
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1, peopleHits = listOf(person)))

        vm.state.test {
            awaitItem()
            vm.setQuery("bryan")
            advanceTimeBy(250)
            runCurrent()

            assertEquals(listOf(person), (awaitItem() as SearchUiState.Ready).people)
        }
    }

    /** The core answering with an error is shown, not crashed past. */
    @Test
    fun aFailedRoundIsShownRatherThanThrown() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(FakeCatalogRepository(searchThrows = IllegalStateException("no index")))

        vm.state.test {
            awaitItem()
            vm.setQuery("steuer")
            advanceTimeBy(250)
            runCurrent()

            val failed = assertIs<SearchUiState.Failed>(awaitItem())
            assertEquals("no index", failed.message)
        }
    }

    /**
     * The ViewModel outlives one visit to the screen — [SearchViewModel.open]
     * is what a second visit uses to stop reporting on the first one's query.
     */
    @Test
    fun reopeningWithABlankQueryDropsWhateverWasFoundBefore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1))

        vm.state.test {
            awaitItem()
            vm.setQuery("steuer")
            advanceTimeBy(250)
            runCurrent()
            assertIs<SearchUiState.Ready>(awaitItem())

            vm.open("")

            assertEquals(SearchUiState.Idle, awaitItem())
        }
    }

    /** A query restored after a killed process is answered the same as one just typed. */
    @Test
    fun reopeningWithARestoredQueryAnswersIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val hit = SearchHit(setId = "movie-0", matched = "title", excerpt = null)
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1, searchHits = listOf(hit)))

        vm.state.test {
            awaitItem()
            vm.open("steuer")
            advanceTimeBy(250)
            runCurrent()

            assertEquals(SearchUiState.Ready(listOf(hit)), awaitItem())
        }
    }

    /**
     * `open` clears a previous, unrelated answer synchronously — before
     * asking anything new, not once the new answer happens to arrive. A
     * screen that reads this ViewModel's state the instant it reopens
     * must never see the last visit's rows, even for the one frame before
     * a fresh search could otherwise have replaced them.
     */
    @Test
    fun openClearsAPreviousAnswerBeforeAskingAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val hit = SearchHit(setId = "movie-0", matched = "title", excerpt = null)
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1, searchHits = listOf(hit)))

        vm.state.test {
            awaitItem()
            vm.setQuery("first")
            advanceTimeBy(250)
            runCurrent()
            assertIs<SearchUiState.Ready>(awaitItem())

            vm.open("second")

            // No time advanced: the reset is synchronous, not the eventual answer to "second".
            assertEquals(SearchUiState.Idle, awaitItem())
        }
    }

    @Test
    fun clearGoesIdleAtOnceRatherThanWaitingOutThePause() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(FakeCatalogRepository(movies = 1))

        vm.state.test {
            awaitItem()
            vm.setQuery("steuer")
            advanceTimeBy(250)
            runCurrent()
            assertIs<SearchUiState.Ready>(awaitItem())

            vm.clear()
            runCurrent()

            assertEquals(SearchUiState.Idle, awaitItem())
        }
    }
}
