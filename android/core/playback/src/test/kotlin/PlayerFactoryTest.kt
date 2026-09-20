package playback

import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Uses Robolectric's real application [Context], not a mock: the
 * database-backed cache needs to actually open a SQLite database, which a
 * mocked `Context` can't provide.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerFactoryTest {

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
    }

    @Test
    fun theCacheWrapsTheMlibSource() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val factory = cacheDataSourceFactory(context) { FakeCore() }

        assertTrue(factory.createDataSource() is CacheDataSource)
    }
}
