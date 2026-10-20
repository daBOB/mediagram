package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.Households

/** The install's households, as the owner's screens and Join a household draw them. */
interface HouseholdsRepository {
    /** What the core last said; an empty list until it first has, which a screen reads as loading. */
    val view: StateFlow<Households>

    /**
     * Reads [view] again. A read that fails throws, and leaves [view] as it
     * was. The caller reads again when a screen opens and after every
     * household request that answered Done.
     */
    suspend fun reload()
}

/**
 * Reads [view] again by itself whenever [WatchStateRepository.profiles] or
 * [WatchStateRepository.syncedOnce] changes: a sync round, a join, or an
 * action that changes who is in this device's household (each goes through
 * [WatchStateRepository.manage], which re-reads the profiles). That does not
 * cover a rename or a change elsewhere, so the caller [reload]s as well.
 * Reads run one at a time, so an older one never overwrites a newer.
 */
class DefaultHouseholdsRepository(
    private val coreProvider: CoreProvider,
    watchState: WatchStateRepository,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HouseholdsRepository {
    private val _view = MutableStateFlow(Households())
    override val view: StateFlow<Households> = _view.asStateFlow()
    private val reading = Mutex()

    init {
        // Latest only: a newer change makes a read still under way stale.
        scope.launch {
            merge(watchState.profiles, watchState.syncedOnce).collectLatest {
                try {
                    reload()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "could not read the households", e)
                }
            }
        }
    }

    override suspend fun reload() {
        reading.withLock {
            val core = coreProvider.awaitCore()
            val read = withContext(dispatcher) { core.households() }.toModel()
            // Home is never deleted, so a view with no household at all is no read: screens keep waiting.
            if (read.list.isNotEmpty()) _view.value = read
        }
    }

    private companion object {
        const val TAG = "HouseholdsRepository"
    }
}
