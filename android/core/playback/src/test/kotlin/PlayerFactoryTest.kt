package playback

import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PlayerFactoryTest {

    @Test
    fun theCacheWrapsTheMlibSource() {
        val context = mockk<Context> {
            every { cacheDir } returns Files.createTempDirectory("mlib-cache-test").toFile()
        }

        val factory = cacheDataSourceFactory(context, FakeCore())

        assertTrue(factory.createDataSource() is CacheDataSource)
    }
}
