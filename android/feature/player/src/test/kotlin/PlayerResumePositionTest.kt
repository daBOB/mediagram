package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where [PlayerViewModel.open] starts a set — [data.ResumePoint]'s glance threshold, exercised through the real view model. */
class PlayerResumePositionTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingAMidTitlePositionResumesTherePastTheGlanceThreshold() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val watch = WatchStateFixture(seed = { setProgress("s1", 1200.0, 2400.0) })
        val vm = buildViewModel(handle, watch.repository)

        vm.open("s1")

        assertEquals(1_200_000L, handle.openedStartAtMs)
    }

    @Test
    fun openingAGlancePositionStartsFromTheTop() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val watch = WatchStateFixture(seed = { setProgress("s1", 12.0, 2400.0) })
        val vm = buildViewModel(handle, watch.repository)

        vm.open("s1")

        assertEquals(0L, handle.openedStartAtMs)
    }

    @Test
    fun openingATitleWithNoRecordedProgressStartsFromTheTop() = runTest {
        installMainDispatcher()
        val handle = FakePlayerHandle()
        val vm = buildViewModel(handle)

        vm.open("s1")

        assertEquals(0L, handle.openedStartAtMs)
    }
}
