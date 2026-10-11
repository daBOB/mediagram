package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextStyle
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** A TV role read under a style is the same role — size, weight, spacing — in that style's one face; mediagram's is untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvStyleTypeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    private fun read(
        style: TvStyle,
        role: @Composable () -> TextStyle,
    ): TextStyle {
        var read: TextStyle? = null
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { CompositionLocalProvider(LocalTvStyle provides style) { read = role() } }
        }
        compose.waitForIdle()
        close()
        return checkNotNull(read)
    }

    @Test
    fun mediagramKeepsEveryRolesOwnFace() {
        assertEquals(TvType.Base.bodyLarge, read(TvStyle.MEDIAGRAM) { TvType.bodyLarge })
        assertEquals(TvType.Base.labelLarge, read(TvStyle.MEDIAGRAM) { TvType.labelLarge })
        assertEquals(CoverTitle, read(TvStyle.MEDIAGRAM) { CoverTitle.inTvStyle() })
    }

    @Test
    fun appleSetsEachRoleInGeistAtItsOwnSizeAndWeight() {
        val body = read(TvStyle.APPLE) { TvType.bodyLarge }
        assertEquals(AppleTvFamily, body.fontFamily)
        assertEquals(TvType.Base.bodyLarge.fontSize, body.fontSize)
        assertEquals(TvType.Base.bodyLarge.fontWeight, body.fontWeight)
    }

    @Test
    fun googleSetsTitlesAndRolesInGoogleSansFlex() {
        assertEquals(GoogleTvFamily, read(TvStyle.GOOGLE) { CoverTitle.inTvStyle() }.fontFamily)
        assertEquals(GoogleTvFamily, read(TvStyle.GOOGLE) { TvType.Eyebrow }.fontFamily)
    }
}
