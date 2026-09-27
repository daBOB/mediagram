package testing

import kotlinx.coroutines.test.runTest
import uniffi.mediagram_core.CoreException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * [FakeCore]'s own contract cases [CoreContract] cannot reach: that shared
 * suite only ever reads an *unknown* set, offline-safe against the real
 * core too. A mutation that dropped only the unknown-set check would still
 * pass it, because a positively-sized, known set's own end is a different
 * branch — this pins that one directly against the fake.
 */
class FakeCoreTest {
    @Test
    fun readingPastAKnownSetsEndIsNotFound() =
        runTest {
            val core = FakeCore(totalSize = 10)

            assertFailsWith<CoreException.NotFound> { core.read("known", 10uL, 1u) }
        }
}
