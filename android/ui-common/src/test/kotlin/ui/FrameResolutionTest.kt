package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The dispatch-level rule behind [ResolvedBranch]: a frame whose own key
 * does not resolve reads differently depending on whether the catalog has
 * answered yet. Pure, so it is tested without a `Composable` — what used
 * to draw nothing at all (N1) was exactly this decision missing.
 */
class FrameResolutionTest {

    @Test
    fun aResolvedKeyShowsItsOwnValue() {
        assertEquals(FrameResolution.Resolved("set-1"), resolveFrame("set-1", catalogReady = false))
        assertEquals(FrameResolution.Resolved("set-1"), resolveFrame("set-1", catalogReady = true))
    }

    /** A restore landing on a title before the catalog has loaded — "not yet", not "gone". */
    @Test
    fun anUnresolvedKeyWhileTheCatalogIsNotReadyIsLoading() {
        assertEquals(FrameResolution.Loading, resolveFrame<String>(null, catalogReady = false))
    }

    /** A menu screen left over a title deleted elsewhere, or a list removed on another device. */
    @Test
    fun anUnresolvedKeyOnceTheCatalogIsReadyIsStale() {
        assertEquals(FrameResolution.Stale, resolveFrame<String>(null, catalogReady = true))
    }
}
