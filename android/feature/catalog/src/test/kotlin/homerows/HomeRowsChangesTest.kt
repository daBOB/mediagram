package homerows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/** The home screen is told when the rows may have changed: a new person or a new snapshot, not the state it started from. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRowsChangesTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val state = MutableStateFlow<Any?>("Ada")
    private var told = 0
    private val changes = HomeRowsChanges(state, scope) { told++ }

    @Test
    fun notifiesOncePerChangeAfterTheFirstValue() {
        changes.start()
        assertEquals(0, told, "the state it started from is no change")

        state.value = "Kim"
        state.value = "Ada"
        assertEquals(2, told)
    }

    @Test
    fun startingTwiceWatchesOnce() {
        changes.start()
        changes.start()

        state.value = "Kim"
        assertEquals(1, told)
    }
}
