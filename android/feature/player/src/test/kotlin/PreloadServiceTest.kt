package player

import testing.FakeFilmPreloading
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * [PreloadService.onDestroy]'s own defensive pause — see the class doc for
 * why. Built through [Robolectric.buildService] for a real, Robolectric-
 * constructed [PreloadService] instance (so [PreloadService]'s own private
 * `scope` field is genuinely initialized, the same as on a device), but
 * deliberately stopping short of [org.robolectric.android.controller.ServiceController.create]:
 * that would run through `Hilt_PreloadService.onCreate`'s own injection
 * call, which needs a real Hilt component this module's test setup does
 * not build — [PreloadService.preloader] is assigned directly instead, the
 * same substitution [PreloadService.onDestroy] alone needs to exercise its
 * logic. [PreloadService.onCreate]/[PreloadService.onTimeout] are not
 * reachable this way and are not covered by this test.
 */
@RunWith(RobolectricTestRunner::class)
class PreloadServiceTest {

    private fun newService(): Pair<PreloadService, FakeFilmPreloading> {
        val service = Robolectric.buildService(PreloadService::class.java).get()
        val preloader = FakeFilmPreloading()
        service.preloader = preloader
        return service to preloader
    }

    @Test
    fun onDestroyPausesForTimeLimitWhenRealWorkIsStillOutstanding() {
        val (service, preloader) = newService()
        preloader.setHasWork(true)

        service.onDestroy()

        assertEquals(1, preloader.pauseForTimeLimitCallCount)
    }

    @Test
    fun onDestroyDoesNothingExtraOnceTheQueueHasAlreadyEmptied() {
        // The ordinary stop path: hasWork already false (nothing enqueued,
        // or everything already finished/cancelled) — nothing left to
        // pause, and onDestroy must not call pauseForTimeLimit blind.
        val (service, preloader) = newService()
        preloader.setHasWork(false)

        service.onDestroy()

        assertEquals(0, preloader.pauseForTimeLimitCallCount)
    }
}
