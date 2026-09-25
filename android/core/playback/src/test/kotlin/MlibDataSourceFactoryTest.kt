package playback

import androidx.media3.datasource.DataSpec
import data.CoreClient
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class MlibDataSourceFactoryTest {
    /**
     * The player is built once per process and outlives signing this device
     * out. A factory that captured its core would keep the previous
     * account's open, still-authorised connection reachable — and since the
     * catalog resolves by path, it would look up the *new* library's sets
     * and fetch them as the *old* account.
     */
    @Test
    fun eachReadSessionIsBoundToWhicheverCoreIsCurrentThen() {
        var current: CoreClient? = coreWithBytes(1_000)
        val factory = MlibDataSourceFactory(PlaybackCounters()) { current }

        val before = factory.createDataSource().open(DataSpec(setUri("s1")))
        current = coreWithBytes(4_000)
        val after = factory.createDataSource().open(DataSpec(setUri("s1")))

        assertEquals(1_000L, before)
        assertEquals(4_000L, after, "a replaced core must be the one the next read session reads through")
    }

    @Test
    fun aReadSessionOpenedWithNoCoreFailsAsIoRatherThanReadingThroughAnOldOne() {
        val factory = MlibDataSourceFactory(PlaybackCounters()) { null }

        assertFailsWith<IOException> { factory.createDataSource().open(DataSpec(setUri("s1"))) }
    }
}
