package ui.tv.chrome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import designsystem.LocalTvStyle
import designsystem.TvStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * The chrome's requesters are one object across frames, so a page handed
 * it (or its rail target) can skip while the cover blends under the bar;
 * a new style builds a new one, since where a rail row's focus goes depends on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvChromeFocusTest : TvScreenStateTest() {
    @Test
    fun theSameFocusIsHandedOutEveryFrameUntilTheStyleChanges() {
        var frame by mutableIntStateOf(0)
        var style by mutableStateOf(TvStyle.MEDIAGRAM)
        val seen = mutableListOf<TvChromeFocus>()
        show {
            CompositionLocalProvider(LocalTvStyle provides style) {
                frame.let { seen += rememberTvChromeFocus() }
            }
        }
        frame++
        compose.waitForIdle()
        assertEquals(2, seen.size, "the frame recomposed")
        assertSame(seen.first(), seen.last())
        val mediagram = seen.last()

        style = TvStyle.GOOGLE
        compose.waitForIdle()
        assertNotSame(mediagram, seen.last())
        assertSame(seen.last().avatarFocus, seen.last().railTarget(RailItem.MY_LIST))
    }
}
