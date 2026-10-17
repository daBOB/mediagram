package data

import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReportChannelTest {
    @Test
    fun sendsTheTextUnchanged() =
        runTest {
            val core = FakeCore()
            CoreReportChannel(FakeCoreProvider(core)).send("Won't play: stalls at 12:03")
            assertEquals(listOf("Won't play: stalls at 12:03"), core.reportsSent)
        }

    @Test
    fun throwsWithoutACore() =
        runTest {
            assertFailsWith<IllegalStateException> { CoreReportChannel(FakeCoreProvider(null)).send("x") }
        }

    @Test
    fun throwsWhenNotSignedIn() =
        runTest {
            val core = FakeCore().apply { authorized = false }
            assertFailsWith<IllegalStateException> { CoreReportChannel(FakeCoreProvider(core)).send("x") }
            assertTrue(core.reportsSent.isEmpty())
        }
}
