package playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/** Delegates every dispatch to [delegate], recording the thread each one actually ran on. */
private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    @Volatile
    var lastDispatchThread: Thread? = null
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context) {
            lastDispatchThread = Thread.currentThread()
            block.run()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class CacheProviderTest {

    private val probeExecutor = Executors.newSingleThreadExecutor()

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
    }

    @After
    fun shutDownTheProbeThread() {
        probeExecutor.shutdown()
    }

    @Test
    fun constructionRunsOnTheGivenDispatcherNotTheCallingThread() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callingThread = Thread.currentThread()
        val recording = RecordingDispatcher(probeExecutor.asCoroutineDispatcher())

        CacheProvider.get(context, recording)

        // Both halves matter: a dispatcher that's never actually invoked
        // would leave lastDispatchThread null, which is also "not equal
        // to callingThread" but proves nothing.
        assertNotNull(recording.lastDispatchThread, "the given dispatcher was never actually used")
        assertNotEquals(callingThread, recording.lastDispatchThread)
    }
}
