package playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testing.FakeCore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Uses Robolectric's real application [Context], not a mock: the
 * database-backed cache needs to actually open a SQLite database, which a
 * mocked `Context` can't provide.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerFactoryTest {
    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
    }

    @After
    fun releaseTheSharedCache() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CacheProvider.get(context).release()
            CacheProvider.resetForTest()
        }

    @Test
    fun theCacheWrapsTheMlibSource() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val factory = cacheDataSourceFactory(context, PlaybackCounters()) { FakeCore() }

            assertTrue(factory.createDataSource() is CacheDataSource)
        }

    @Test
    fun repeatedBoundedReadsUseCachedBytesWithoutAnotherCoreRead() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val core = FakeCore(totalSize = 32_768, bytesOf = { offset, len -> ByteArray(len) { ((offset + it) % 251).toByte() } })
            val counters = PlaybackCounters()
            val factory = cacheDataSourceFactory(context, counters) { core }
            val start = 73L
            val length = 8193
            val request =
                DataSpec
                    .Builder()
                    .setUri(setUri("bounded-cache-read"))
                    .setPosition(start)
                    .setLength(length.toLong())
                    .build()

            fun readRange(): ByteArray {
                val source = factory.createDataSource()
                try {
                    assertEquals(length.toLong(), source.open(request))
                    val bytes = ByteArray(length)
                    var received = 0
                    while (received < length) {
                        val count = source.read(bytes, received, minOf(777, length - received))
                        assertTrue(count > 0, "the requested range must not end early")
                        received += count
                    }
                    assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
                    return bytes
                } finally {
                    source.close()
                }
            }

            val expected = ByteArray(length) { ((start + it) % 251).toByte() }
            assertContentEquals(expected, readRange())
            val initialReads = core.reads
            assertTrue(initialReads > 0)
            // The 32,768-byte set is one chunk, fetched whole — chunk-aligned
            // reads always pull the full chunk a bounded request lands in,
            // not just the DataSpec's own shorter span.
            assertEquals(32_768L, counters.totals().fromUpstreamBytes)
            assertEquals(0L, counters.totals().fromCacheBytes)
            assertEquals(length.toLong(), CacheProvider.occupancy(context).heldBytes)

            assertContentEquals(expected, readRange())
            assertEquals(initialReads, core.reads)
            assertEquals(32_768L, counters.totals().fromUpstreamBytes)
            assertEquals(length.toLong(), counters.totals().fromCacheBytes)
            assertEquals(0, counters.totals().failedReads)
        }

    /**
     * Both directions, explicitly, and the one number every skip reads.
     * media3 defaults to five seconds back and fifteen forward, so a card
     * whose buttons both say fifteen would be telling a viewer something
     * the player does not do going back.
     */
    @Test
    fun aSkipMovesFifteenSecondsInEitherDirection() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val player = buildPlayer(context, PlaybackCounters()) { FakeCore() }

            try {
                assertEquals(15_000L, SKIP_MS)
                assertEquals(SKIP_MS, player.seekBackIncrement)
                assertEquals(SKIP_MS, player.seekForwardIncrement)
            } finally {
                player.release()
            }
        }

    /**
     * This app's subtitles are the index's own VTT text, drawn by
     * `SubtitleLayer` — never a container's embedded track. Disabling the
     * type outright is what keeps a forced or default track from a
     * container ever flashing up uninvited, matching the web, which never
     * extracts one to begin with.
     */
    @Test
    fun theTextRendererIsDisabled() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val player = buildPlayer(context, PlaybackCounters()) { FakeCore() }

        try {
            assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        } finally {
            player.release()
        }
    }

    /**
     * DTS and TrueHD have no decoder on many devices, and a track no renderer
     * takes plays silently. FFmpeg has to be in the list, and behind the
     * platform's own audio renderer so hardware and passthrough still win
     * wherever they exist.
     */
    @Test
    fun ffmpegAudioFollowsThePlatformAudioRenderer() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val renderers =
            renderersFactory(context).createRenderers(
                Handler(Looper.getMainLooper()),
                object : VideoRendererEventListener {},
                object : AudioRendererEventListener {},
                { _: CueGroup -> },
                { _: Metadata -> },
            )

        val platform = renderers.indexOfFirst { it is MediaCodecAudioRenderer }
        val ffmpeg = renderers.indexOfFirst { it is FfmpegAudioRenderer }
        assertTrue(platform >= 0, "the platform audio renderer is missing")
        assertTrue(ffmpeg > platform, "FFmpeg must come after the platform audio renderer, not before or never")
        renderers.forEach { it.release() }
    }

    /**
     * A box that claims HDMI passthrough for every format — as the Realtek
     * Google TV box does — still gets DTS and TrueHD decoded by FFmpeg: the
     * sink refuses them, so the platform renderer, with no decoder of its own
     * for either, declines the track. AC-3, E-AC-3 and PCM go straight on.
     */
    @Test
    fun theAudioSinkRefusesDtsAndTrueHdPassthroughButNotDolbyDigitalOrPcm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val claimsEverything =
            object : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {
                override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
        val sink = NoLosslessPassthroughSink(claimsEverything)
        fun support(mime: String) = sink.getFormatSupport(Format.Builder().setSampleMimeType(mime).setChannelCount(6).setSampleRate(48_000).build())

        for (refused in listOf(MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_X, MimeTypes.AUDIO_TRUEHD)) {
            assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, support(refused), "$refused must not go out as passthrough")
        }
        for (kept in listOf(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_RAW)) {
            assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, support(kept), "$kept must still pass through")
        }
    }

    @Test
    fun theFactoryBuildsItsAudioSinkBehindTheGuard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = renderersFactory(context) as PlaybackRenderersFactory

        assertTrue(factory.audioSinkFor(context) is NoLosslessPassthroughSink)
    }
}
