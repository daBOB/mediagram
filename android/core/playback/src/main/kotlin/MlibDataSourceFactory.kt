// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import androidx.media3.datasource.DataSource
import data.CoreClient
import java.io.IOException

/**
 * Hands ExoPlayer a fresh [MlibDataSource] per read session, bound to
 * whichever core is current *then*, over a [ChunkMemo] shared by every
 * session this factory ever opens — see [ChunkMemo] for why that sharing
 * is what keeps a `CacheDataSource` gap-fill from re-downloading a chunk
 * one of this factory's other data sources already has.
 *
 * The core a session opens with is asked for each time rather than
 * captured once: the player is built once per process and outlives a
 * start-over, and the core it would otherwise have kept holds the previous
 * account's open, still-authorised connection. Deleting the auth key file
 * does not close that connection, and the catalog it reads resolves by
 * path — so a captured core would look up the new library's sets and fetch
 * them as the old account, which is the opposite of what signing out is
 * supposed to mean.
 *
 * The upstream this feeds [ChunkMemo] follows the same reasoning,
 * re-resolving the current core on every chunk it actually fetches rather
 * than freezing whichever core this factory saw first. That the memo can
 * go on to serve an old entry under a different core than fetched it is not
 * a new risk this introduces: a `setId` names one Telegram message in one
 * channel, so the bytes behind it cannot change from one core to the next,
 * and the on-disk `CacheDataSource` cache beneath this already persists the
 * same keys across a sign-out with no guard at all.
 *
 * [lan], when given, sits between the memo and Telegram
 * ([LanFirstChunkSource]) — `null` is a plain Telegram-only factory, which
 * is what every existing caller and test still gets by not passing one.
 * The preloader shares this same factory (through `cacheDataSourceFactory`
 * below), which is how a series preload fills the LAN server for free: it
 * is not a separate path that happens to agree with playback's, it is the
 * same one.
 */
class MlibDataSourceFactory(
    private val counters: PlaybackCounters,
    private val lan: LanCacheRuntime? = null,
    private val currentCore: () -> CoreClient?,
) : DataSource.Factory {
    private val chunks: SetChunkSource = ChunkMemo(upstream = buildUpstream())

    private fun buildUpstream(): SetChunkSource {
        val telegram =
            SetChunkSource { setId, index, totalSize ->
                val core = currentCore() ?: throw IOException("this device is not set up to read the library")
                TelegramChunkSource(core, counters).chunk(setId, index, totalSize)
            }
        val runtime = lan ?: return telegram
        return LanFirstChunkSource(
            lan = runtime.client,
            telegram = telegram,
            network = runtime.network,
            server = { runtime.server() },
            writes = runtime.writes,
            counters = counters,
        )
    }

    override fun createDataSource(): DataSource = MlibDataSource(currentCore(), chunks)
}
