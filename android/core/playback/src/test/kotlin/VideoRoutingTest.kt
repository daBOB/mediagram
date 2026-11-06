// media3's renderer surface is @UnstableApi; see CacheProvider for why the version is pinned.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testing.FakeCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two video renderers and the switch between them: effects (the
 * deinterlacing filter) may only ever reach the one renderer that is
 * disabled the rest of the time, so every other title keeps the direct path.
 */
@RunWith(RobolectricTestRunner::class)
class VideoRoutingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val avc = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(720).setHeight(576).build()

    @Before
    fun resetTheSharedCache() = CacheProvider.resetForTest()

    @After
    fun releaseTheSharedCache() =
        runBlocking {
            CacheProvider.get(context).release()
            CacheProvider.resetForTest()
        }

    private fun renderers(): Array<Renderer> =
        renderersFactory(context).createRenderers(
            Handler(Looper.getMainLooper()),
            object : VideoRendererEventListener {},
            object : AudioRendererEventListener {},
            { _: CueGroup -> },
            { _: Metadata -> },
        )

    /** media3 keeps the list a renderer was handed in a private field; there is no getter to ask. */
    private fun heldEffects(renderer: Renderer): Any? =
        MediaCodecVideoRenderer::class.java.getDeclaredField("videoEffects").apply { isAccessible = true }.get(renderer)

    @Test
    fun theFactoryBuildsTheEffectsRendererAheadOfThePlainOne() {
        val built = renderers()

        val effects = built.indexOfFirst { it is EffectsVideoRenderer }
        val plain = built.indexOfFirst { it is PlainVideoRenderer }
        assertTrue(effects >= 0 && plain >= 0, "both video renderers must be built")
        assertTrue(effects < plain, "the effects renderer has to list first, or the plain one would always win selection")
        assertEquals(2, built.count { it is MediaCodecVideoRenderer })
        built.forEach { it.release() }
    }

    @Test
    fun thePlainRendererDropsEffectsAndTheEffectsRendererKeepsThem() {
        val built = renderers()
        val plain = built.first { it is PlainVideoRenderer }
        val effects = built.first { it is EffectsVideoRenderer }
        val list = listOf(DeinterlaceEffect { 576 })

        plain.handleMessage(Renderer.MSG_SET_VIDEO_EFFECTS, list)
        effects.handleMessage(Renderer.MSG_SET_VIDEO_EFFECTS, list)

        assertNull(heldEffects(plain), "a plain renderer given effects would build a GL graph it never drops")
        assertEquals(list, heldEffects(effects))
        built.forEach { it.release() }
    }

    @Test
    fun theEffectsRendererClaimsNothingUntilItIsActive() {
        val effects = renderers().first { it is EffectsVideoRenderer } as EffectsVideoRenderer

        assertEquals(C.FORMAT_UNSUPPORTED_TYPE, RendererCapabilities.getFormatSupport(effects.supportsFormat(avc)))
        effects.active = true
        assertTrue(RendererCapabilities.getFormatSupport(effects.supportsFormat(avc)) != C.FORMAT_UNSUPPORTED_TYPE)
        effects.release()
    }

    private fun withPlayer(block: (ExoPlayer, EffectsVideoRenderer, Int) -> Unit) = runTest {
        val player = buildPlayer(context, PlaybackCounters()) { FakeCore() }
        try {
            val index = (0 until player.rendererCount).first { player.getRenderer(it) is EffectsVideoRenderer }
            block(player, player.getRenderer(index) as EffectsVideoRenderer, index)
        } finally {
            player.release()
        }
    }

    private fun ExoPlayer.effectsRendererDisabled(index: Int) = (trackSelector as DefaultTrackSelector).parameters.getRendererDisabled(index)

    @Test
    fun aPlayerStartsOnThePlainRenderer() = withPlayer { _, effects, _ -> assertTrue(!effects.active) }

    @Test
    fun deinterlaceRoutesVideoToTheEffectsRendererAndOffRoutesItBack() =
        withPlayer { player, effects, index ->
            player.routeVideo(deinterlace = true)
            assertTrue(effects.active)
            assertTrue(!player.effectsRendererDisabled(index))

            player.routeVideo(deinterlace = false)
            assertTrue(!effects.active)
            assertTrue(player.effectsRendererDisabled(index))
        }

    @Test
    fun hdrNeverGoesThroughTheEffectsRenderer() =
        withPlayer { player, effects, index ->
            player.routeVideo(deinterlace = true, hdr = true)

            assertTrue(!effects.active)
            assertTrue(player.effectsRendererDisabled(index))
        }
}
