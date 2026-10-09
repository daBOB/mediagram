package catalog

import app.cash.turbine.test
import data.CatalogEnrichmentFetcher
import data.LibraryUpdateCoordinator
import data.LiveSession
import data.WatchStateRepository
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.Kind
import model.Profile
import model.together.Member
import model.together.Session
import org.junit.After
import testing.FakeCore
import testing.FakeCoreProvider
import testing.FakeTogetherLive
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What Home and the title pages are offered to join: the live session, on a
 * title this profile's own catalog holds, while Telegram can be reached —
 * the web's `joinable()` in `together-join.js`.
 */
class CatalogTogetherTest {
    private val family = fakeSet(Kind.MOVIE, "family").copy(title = "Family Film", fsk = "6")
    private val grown = fakeSet(Kind.MOVIE, "grown").copy(title = "Grown Film", fsk = "16")
    private val episode = fakeSet(Kind.EPISODE, "ep").copy(title = "Pilot", show = "A Show", season = 1, episodeFirst = 2)

    private fun live(
        set: String,
        vararg labels: String,
    ) = LiveSession(
        id = 41,
        session =
            Session(
                session = "s",
                set = set,
                playing = true,
                pos = 0.0,
                rate = 1.0,
                at = 0,
                by = "tv",
                members = labels.map { Member(it.lowercase(), it) },
                ended = false,
            ),
    )

    private fun catalog(
        together: FakeTogetherLive,
        watch: WatchStateRepository = WatchStateFixture(listOf(Profile("a", "Ana"))).repository,
    ): CatalogViewModel {
        val repository = FakeCatalogRepository(given = listOf(family, grown, episode))
        val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings())
        return CatalogViewModel(repository, watch, LibraryUpdateCoordinator(repository, enrichment), togetherLive = together)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aSessionOnATitleInTheCatalogIsOfferedWithWhoIsInIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalog(FakeTogetherLive(live("grown", "TV", "Tablet")))
            vm.together.test {
                assertNull(awaitItem())
                val offer = awaitItem()
                assertEquals(TogetherOffer(41, grown, "TV, Tablet"), offer)
                assertEquals("Watching together: Grown Film · TV, Tablet", offer?.line)
            }
        }

    @Test
    fun anEpisodeIsNamedByItsShowAndNumberAsTheWebNamesIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalog(FakeTogetherLive(live("ep", "TV")))
            vm.together.test {
                assertNull(awaitItem())
                assertEquals("Watching together: A Show S1 E2 · TV", awaitItem()?.line)
            }
        }

    @Test
    fun aKidsProfileIsNotOfferedATitlePastItsLimit() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val kid = WatchStateFixture(listOf(Profile("k", "Mia", kids = true))).repository
            val together = FakeTogetherLive(live("grown", "TV"))
            val vm = catalog(together, kid)
            vm.together.test {
                assertNull(awaitItem())
                runCurrent()
                expectNoEvents()
                // The same session on a title the kid's catalog holds is offered.
                together.live.value = live("family", "TV")
                assertEquals(family, awaitItem()?.set)
            }
        }

    @Test
    fun nothingIsOfferedWhileTelegramIsOutOfReach() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val together = FakeTogetherLive(live("family", "TV"), available = false)
            val vm = catalog(together)
            vm.together.test {
                assertNull(awaitItem())
                runCurrent()
                expectNoEvents()
                together.available.value = true
                assertEquals(family, awaitItem()?.set)
            }
        }

    @Test
    fun aCourseOffersJoinForALessonFiledDeepInIt() {
        val lesson = fakeSet(Kind.TUTORIAL, "lesson")
        val deep = Division("Part 1", null, emptyList(), listOf(Division("Chapter 2", null, listOf(lesson), emptyList())))
        val course = Entry.Collection("c", CollectionKind.COURSE, "A Course", null, null, 1, 1, listOf(deep))

        assertTrue(TogetherOffer(41, lesson, "TV").playsIn(course))
        assertFalse(TogetherOffer(41, family, "TV").playsIn(course))
    }

    @Test
    fun joiningAsksThePlayerThatOpensNextToJoinThatSession() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val together = FakeTogetherLive(live("family", "TV"))
            val vm = catalog(together)

            vm.join(TogetherOffer(41, family, "TV"))

            assertEquals(41, together.takeJoin())
        }

    @Test
    fun showingJoinReadsTheSessionsAgainAndNobodyShowingItStopsListening() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val together = FakeTogetherLive(live("family", "TV"))
            val vm = catalog(together)
            assertEquals(0, together.refreshCount)

            vm.together.test {
                awaitItem()
                runCurrent()
                assertEquals(1, together.refreshCount)
                assertEquals(1, together.live.subscriptionCount.value)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(5_001)
            assertEquals(0, together.live.subscriptionCount.value)

            vm.together.test {
                awaitItem()
                runCurrent()
                assertEquals(2, together.refreshCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
