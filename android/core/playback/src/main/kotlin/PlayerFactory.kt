// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import uniffi.mediagram_core.CoreInterface

/**
 * Wraps [MlibDataSourceFactory] in the process-wide disk cache: a cache hit
 * never reaches the core, a miss falls through to [MlibDataSource]. `suspend`
 * because building the cache does real disk/database I/O — see
 * [CacheProvider.get].
 *
 * Returns the concrete [CacheDataSource.Factory] rather than the plain
 * [DataSource.Factory] interface it also is — [CacheDataSourceWriter] needs
 * a real [CacheDataSource] to hand a media3 `CacheWriter`, and this is the
 * one place that builds one.
 */
suspend fun cacheDataSourceFactory(
    context: Context,
    counters: PlaybackCounters,
    lan: LanCacheRuntime? = null,
    currentCore: () -> CoreInterface?,
): CacheDataSource.Factory = cacheDataSourceFactory(CacheProvider.get(context), counters, lan, currentCore = currentCore)

/**
 * What the player reads through: [cacheDataSourceFactory] that treats a
 * failing cache as absent. A full disk or a pulled card then costs the
 * rest of that title its cache, not the viewer their playback: media3
 * remembers the error on the data source, and the player keeps one per
 * title, so the next title opens the cache again.
 *
 * Only the player. Preload keeps the strict factory: a preload into a
 * cache that cannot hold anything would download whole episodes and keep
 * none of them, again on every trigger, so there a failure has to stop it.
 *
 * Only the player reads ahead, too — see [MlibDataSourceFactory].
 */
internal fun playbackDataSourceFactory(
    cache: Cache,
    counters: PlaybackCounters,
    lan: LanCacheRuntime? = null,
    currentCore: () -> CoreInterface?,
): CacheDataSource.Factory =
    cacheDataSourceFactory(cache, counters, lan, readAhead = true, currentCore = currentCore)
        // setFlags replaces rather than adds; the base factory sets none.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

internal fun cacheDataSourceFactory(
    cache: Cache,
    counters: PlaybackCounters,
    lan: LanCacheRuntime? = null,
    readAhead: Boolean = false,
    currentCore: () -> CoreInterface?,
): CacheDataSource.Factory =
    CacheDataSource
        .Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(MlibDataSourceFactory(counters, lan, readAhead, currentCore))
        // media3 offers this and nothing has ever attached one. Without it there
        // is no way to tell a cache that is carrying playback from one that is
        // being bypassed, which is the first thing worth knowing about a read.
        // Not a SAM conversion: CacheDataSource.EventListener has two abstract
        // methods, so it needs an explicit implementation rather than a lambda.
        .setEventListener(
            object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(
                    cacheSizeBytes: Long,
                    cachedBytesRead: Long,
                ) {
                    counters.servedFromCache(cachedBytesRead)
                }

                override fun onCacheIgnored(reason: Int) = Unit
            },
        )

/**
 * An [ExoPlayer] that reads every set through the cache. No format hints
 * are given to [DefaultMediaSourceFactory] — its default
 * `DefaultExtractorsFactory` sniffs the container, so Matroska, MP4 and
 * whatever else the uploader wrote all work without per-title
 * configuration, and nothing here transcodes anything.
 *
 * `suspend`, not because building an `ExoPlayer` itself is slow, but
 * because opening the cache is: a caller that awaits this from a
 * main-dispatched coroutine resumes the cheap `ExoPlayer.Builder().build()`
 * call back on its own (main) thread once the cache's I/O — the only real
 * work here — has finished on whatever dispatcher [CacheProvider.get] used.
 *
 * The text renderer is disabled outright, once, here — never per open. This
 * app's subtitles are never embedded in the container (the web never
 * extracts one either; both read the index's own VTT text instead — see
 * `SubtitleTrack.kt`), so there is nothing for ExoPlayer's own text
 * selection to offer, and disabling it rules out a forced or default track
 * a container happens to carry ever flashing up uninvited.
 */
suspend fun buildPlayer(
    context: Context,
    counters: PlaybackCounters,
    lan: LanCacheRuntime? = null,
    currentCore: () -> CoreInterface?,
): ExoPlayer =
    ExoPlayer
        .Builder(context, renderersFactory(context))
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(playbackDataSourceFactory(CacheProvider.get(context), counters, lan, currentCore)),
        )
        // Set in both directions because media3's defaults are not
        // symmetrical — five seconds back, fifteen forward. A control that
        // offers the same jump each way has to say so here; the buttons read
        // their labels back off the player rather than carry their own copy.
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        // On a television the player screen picks the display mode itself
        // (its frame-rate effect), so media3's switching is turned off to
        // leave one mechanism in charge. Phones and tablets keep the default,
        // which makes the seamless switches their panels allow. Reads
        // UiModeManager itself because the shared isTelevision helper does
        // not reach this module; it should use that helper.
        .apply { if (onTelevision(context)) setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF) }
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

private fun onTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
        .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * The device's own decoders first, FFmpeg (core:ffmpeg) behind them.
 *
 * Many devices — Google TV boxes above all — have no DTS or TrueHD decoder,
 * and a track no renderer can take is simply left unselected: the film plays
 * with no sound and no message. `ON` appends `FfmpegAudioRenderer` after
 * `MediaCodecAudioRenderer`, and the track selector takes the first renderer
 * that handles a format, so hardware (and passthrough) still wins wherever it
 * exists and FFmpeg only picks up what nothing else can. `PREFER` would put
 * FFmpeg first and software-decode AAC, AC-3 and E-AC-3 too, for no gain.
 *
 * `internal` so a test can see which renderers it builds.
 */
internal fun renderersFactory(context: Context): DefaultRenderersFactory =
    PlaybackRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

/** [DefaultRenderersFactory] whose audio sink refuses DTS and TrueHD passthrough — see [NoLosslessPassthroughSink]. */
internal class PlaybackRenderersFactory(
    context: Context,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let(::NoLosslessPassthroughSink)

    /** The sink the renderers get, for a test to check what it is. */
    internal fun audioSinkFor(context: Context): AudioSink? = buildAudioSink(context, false, false)
}

/**
 * How far one skip moves, every way a viewer can skip: the card's −15 and
 * +15, the phone's double tap, picture-in-picture's own buttons and the
 * television's D-pad — the web's `SKIP_SECONDS`, so a viewer who uses more
 * than one surface skips the same distance on each. Long enough to clear a
 * missed line and a beat of the scene around it in one press.
 */
const val SKIP_MS = 15_000L
