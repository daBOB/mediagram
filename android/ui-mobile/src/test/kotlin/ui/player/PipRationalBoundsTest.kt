package ui.player

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue
import org.robolectric.annotation.Config

/**
 * Covers [pipAspectRational] at the platform's own extremes — the
 * regression `PipParamsTest`'s `Double`-only assertions could not catch:
 * `(1.0 / 2.39 * 1000).toInt() / 1000.0` truncates to `0.418`, narrower
 * than the true minimum `1/2.39 = 0.41841…`, which `setPictureInPictureParams`
 * rejects as "too extreme". Every value here must sit inside the
 * framework's own accepted range, never short of it. Runs under
 * Robolectric: `android.util.Rational`'s own methods are Android SDK
 * stubs outside it — left zeroed rather than throwing under the plain
 * unit-test jar, which a `<=` or `>=` assertion could pass by accident.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PipRationalBoundsTest {

    @Test
    fun theNarrowestAcceptedWindowIsNotNarrowerThanTheTruePlatformMinimum() {
        val rational = pipAspectRational(1080, 3840) // far narrower than 1:2.39

        assertTrue(rational.toDouble() >= 1.0 / 2.39)
    }

    @Test
    fun theWidestAcceptedWindowIsNotWiderThanTheTruePlatformMaximum() {
        val rational = pipAspectRational(3840, 1200) // far wider than 2.39:1

        assertTrue(rational.toDouble() <= 2.39)
    }

    @Test
    fun anOrdinaryAspectIsExactRatherThanADecimalApproximation() {
        val rational = pipAspectRational(1920, 1080)

        assertTrue(rational.toDouble() == 1920.0 / 1080.0)
    }
}
