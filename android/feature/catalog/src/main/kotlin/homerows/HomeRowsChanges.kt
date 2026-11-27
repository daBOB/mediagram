package homerows

import android.content.Context
import data.WatchStateRepository
import data.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the TV home screen its rows may have changed: whenever [state] moves on from the value it
 * started at. Started by [HomeRowsProvider] on its first ask, so nothing watches for a home screen
 * that never asked.
 */
@Singleton
class HomeRowsChanges(
    private val state: Flow<*>,
    private val scope: CoroutineScope,
    private val notify: () -> Unit,
) {
    /** Who is watching and their snapshot, told to [HomeRowsProvider.CHANGES]. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        watchState: WatchStateRepository,
        @AppScope scope: CoroutineScope,
    ) : this(
        combine(watchState.chosenProfileId, watchState.snapshot) { id, snapshot -> id to snapshot },
        scope,
        { context.contentResolver.notifyChange(HomeRowsProvider.CHANGES, null) },
    )

    private val started = AtomicBoolean(false)

    /** Starts watching; a second call does nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { state.drop(1).collect { notify() } }
    }
}
